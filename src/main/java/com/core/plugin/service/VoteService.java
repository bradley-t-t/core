package com.core.plugin.service;

import com.core.plugin.CorePlugin;
import com.core.plugin.lang.Lang;
import com.core.plugin.modules.vote.VoteRewardHandler;
import com.core.plugin.util.SoundUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Implements both Votifier v1 (RSA) and NuVotifier v2 (token/HMAC) protocols
 * on the same port. Server list sites can use either protocol.
 */
public final class VoteService implements Service {

    private static final int RSA_BLOCK_SIZE = 256;
    private static final int RSA_KEY_BITS = 2048;
    private static final String RSA_CIPHER = "RSA/ECB/PKCS1Padding";
    private static final String HMAC_ALGO = "HmacSHA256";

    private final CorePlugin plugin;
    private final File keyFile;
    private final VoteRewardHandler rewardHandler;

    private KeyPair keyPair;
    private String token;
    private ServerSocket serverSocket;
    private Thread listenerThread;
    private volatile boolean running;

    public VoteService(CorePlugin plugin) {
        this.plugin = plugin;
        this.keyFile = new File(plugin.getDataFolder(), "votifier.yml");
        this.rewardHandler = new VoteRewardHandler(plugin);
    }

    @Override
    public void enable() {
        if (!plugin.getConfig().getBoolean("voting.enabled", true)) {
            plugin.getLogger().info("Vote system disabled in config.");
            return;
        }

        try {
            loadOrGenerateKeys();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize Votifier keys", e);
            return;
        }

        int port = plugin.getConfig().getInt("voting.port", 27917);
        startSocketListener(port);
    }

    @Override
    public void disable() {
        running = false;
        if (serverSocket != null && !serverSocket.isClosed()) {
            try { serverSocket.close(); } catch (IOException ignored) {}
        }
    }

    public VoteRewardHandler rewardHandler() { return rewardHandler; }

    public int getVoteCount(UUID playerId) {
        return plugin.dataManager().getVoteCount(playerId);
    }

    void processVote(String username, String serviceName) {
        plugin.getLogger().info("Vote received for " + username + " from " + serviceName);

        Player player = Bukkit.getPlayerExact(username);
        if (player != null) {
            incrementVoteCount(player.getUniqueId());
            rewardHandler.giveRewards(player);
            Lang.send(player, "vote.thank-you");
            SoundUtil.success(player);
        } else {
            UUID offlineId = resolveOfflineUuid(username);
            if (offlineId != null) {
                incrementVoteCount(offlineId);
                rewardHandler.addPendingReward(offlineId);
            }
        }

        for (Player online : Bukkit.getOnlinePlayers()) {
            Lang.send(online, "vote.received", "player", username);
        }
    }

    private void incrementVoteCount(UUID playerId) {
        plugin.dataManager().setVoteCount(playerId, plugin.dataManager().getVoteCount(playerId) + 1);
    }

    @SuppressWarnings("deprecation")
    private UUID resolveOfflineUuid(String username) {
        var offlinePlayer = Bukkit.getOfflinePlayer(username);
        return offlinePlayer.hasPlayedBefore() ? offlinePlayer.getUniqueId() : null;
    }

    // --- Socket listener ---

    private void startSocketListener(int port) {
        try {
            serverSocket = new ServerSocket(port);
            running = true;
            listenerThread = new Thread(this::acceptConnections, "Core-VoteListener");
            listenerThread.setDaemon(true);
            listenerThread.start();
            plugin.getLogger().info("Votifier listener started on port " + port + " (v1 + v2)");
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to start Votifier on port " + port, e);
        }
    }

    private void acceptConnections() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                handleConnection(socket);
            } catch (SocketException e) {
                if (running) plugin.getLogger().log(Level.WARNING, "Vote socket error", e);
            } catch (Exception e) {
                if (running) plugin.getLogger().log(Level.WARNING, "Error accepting vote connection", e);
            }
        }
    }

    private void handleConnection(Socket socket) {
        try (socket) {
            socket.setSoTimeout(5000);

            // Send v2-style greeting with challenge
            String challenge = generateChallenge();
            String greeting = "VOTIFIER 2 " + challenge + "\n";
            OutputStream out = socket.getOutputStream();
            out.write(greeting.getBytes(StandardCharsets.UTF_8));
            out.flush();

            // Read response — peek at first bytes to detect protocol
            InputStream in = socket.getInputStream();
            BufferedInputStream buffered = new BufferedInputStream(in);
            buffered.mark(2);
            int firstByte = buffered.read();
            buffered.reset();

            if (firstByte == '{') {
                // NuVotifier v2 — JSON with HMAC
                handleV2(buffered, challenge);
            } else {
                // Votifier v1 — raw RSA block
                handleV1(buffered);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error handling vote connection", e);
        }
    }

    // --- V1 Protocol (RSA) ---

    private void handleV1(InputStream in) throws IOException {
        byte[] encrypted = readExactly(in, RSA_BLOCK_SIZE);
        if (encrypted == null) return;

        String decrypted = decryptRsa(encrypted);
        if (decrypted == null) return;

        String[] lines = decrypted.split("\n");
        if (lines.length < 5 || !"VOTE".equals(lines[0])) {
            plugin.getLogger().warning("Malformed v1 vote: " + decrypted.replace("\n", "\\n"));
            return;
        }

        String serviceName = lines[1];
        String username = lines[2];
        Bukkit.getScheduler().runTask(plugin, () -> processVote(username, serviceName));
    }

    private String decryptRsa(byte[] encrypted) {
        try {
            Cipher cipher = Cipher.getInstance(RSA_CIPHER);
            cipher.init(Cipher.DECRYPT_MODE, keyPair.getPrivate());
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to decrypt v1 vote", e);
            return null;
        }
    }

    // --- V2 Protocol (Token/HMAC) ---

    private void handleV2(InputStream in, String challenge) throws IOException {
        // Read the full JSON message
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[1024];
        int read;
        while ((read = in.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
            if (buffer.size() > 64000) break; // safety limit
        }

        String raw = buffer.toString(StandardCharsets.UTF_8).trim();
        if (raw.isEmpty()) return;

        // Parse JSON manually (no library dependency)
        String payload = extractJsonString(raw, "payload");
        String signature = extractJsonString(raw, "signature");

        if (payload == null || signature == null) {
            plugin.getLogger().warning("Malformed v2 vote JSON: " + raw);
            return;
        }

        // Verify HMAC signature
        if (!verifyHmac(payload, signature)) {
            plugin.getLogger().warning("v2 vote HMAC verification failed");
            return;
        }

        // Decode payload (it's base64-encoded JSON)
        String payloadJson = new String(Base64.getDecoder().decode(payload), StandardCharsets.UTF_8);

        // Verify challenge
        String payloadChallenge = extractJsonString(payloadJson, "challenge");
        if (!challenge.equals(payloadChallenge)) {
            plugin.getLogger().warning("v2 vote challenge mismatch");
            return;
        }

        String username = extractJsonString(payloadJson, "username");
        String serviceName = extractJsonString(payloadJson, "serviceName");

        if (username == null || username.isEmpty()) {
            plugin.getLogger().warning("v2 vote missing username");
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () ->
                processVote(username, serviceName != null ? serviceName : "unknown"));
    }

    private boolean verifyHmac(String payload, String signatureBase64) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(token.getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
            byte[] expected = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            byte[] actual = Base64.getDecoder().decode(signatureBase64);
            return MessageDigest.isEqual(expected, actual);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "HMAC verification error", e);
            return false;
        }
    }

    /** Extract a string value from a JSON object by key. Minimal parser — no library needed. */
    private String extractJsonString(String json, String key) {
        String search = "\"" + key + "\"";
        int keyIdx = json.indexOf(search);
        if (keyIdx == -1) return null;

        int colonIdx = json.indexOf(":", keyIdx + search.length());
        if (colonIdx == -1) return null;

        // Skip whitespace after colon
        int valueStart = colonIdx + 1;
        while (valueStart < json.length() && json.charAt(valueStart) == ' ') valueStart++;
        if (valueStart >= json.length()) return null;

        if (json.charAt(valueStart) == '"') {
            // String value
            int end = json.indexOf('"', valueStart + 1);
            if (end == -1) return null;
            return json.substring(valueStart + 1, end);
        }

        // Non-string value (shouldn't happen for our fields, but handle gracefully)
        int end = valueStart;
        while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}') end++;
        return json.substring(valueStart, end).trim();
    }

    // --- Utility ---

    private byte[] readExactly(InputStream in, int length) throws IOException {
        byte[] buf = new byte[length];
        int totalRead = 0;
        while (totalRead < length) {
            int read = in.read(buf, totalRead, length - totalRead);
            if (read == -1) return null;
            totalRead += read;
        }
        return buf;
    }

    private String generateChallenge() {
        byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes).substring(0, 22);
    }

    // --- Key/Token management ---

    private void loadOrGenerateKeys() throws Exception {
        org.bukkit.configuration.file.YamlConfiguration config;

        if (keyFile.exists()) {
            config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(keyFile);
            String publicKeyStr = config.getString("public-key");
            String privateKeyStr = config.getString("private-key");
            token = config.getString("token");

            if (publicKeyStr != null && privateKeyStr != null && token != null) {
                KeyFactory kf = KeyFactory.getInstance("RSA");
                PublicKey pub = kf.generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyStr)));
                PrivateKey priv = kf.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKeyStr)));
                keyPair = new KeyPair(pub, priv);
                plugin.getLogger().info("Loaded Votifier keys from votifier.yml");
                return;
            }
        }

        // Generate everything fresh
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(RSA_KEY_BITS);
        keyPair = gen.generateKeyPair();

        // Generate a random token for v2
        byte[] tokenBytes = new byte[32];
        new SecureRandom().nextBytes(tokenBytes);
        token = Base64.getEncoder().encodeToString(tokenBytes);

        String pubBase64 = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        String privBase64 = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());

        String pubPem = "-----BEGIN PUBLIC KEY-----\n" + formatBase64(pubBase64) + "-----END PUBLIC KEY-----";

        config = new org.bukkit.configuration.file.YamlConfiguration();
        config.options().header(
                "Votifier Keys - AUTO GENERATED\n"
                + "Supports both Votifier v1 (RSA) and NuVotifier v2 (token).\n\n"
                + "For server list sites:\n"
                + "  Port: " + plugin.getConfig().getInt("voting.port", 27917) + "\n"
                + "  Token: copy the 'token' value below\n"
                + "  Public Key: copy the 'public-key-pem' value below\n\n"
                + "DO NOT share the private-key or token publicly.");
        config.set("token", token);
        config.set("public-key", pubBase64);
        config.set("public-key-pem", pubPem);
        config.set("private-key", privBase64);
        config.save(keyFile);

        plugin.getLogger().info("Generated Votifier keys + token. See plugins/Core/votifier.yml");
    }

    private String formatBase64(String base64) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < base64.length(); i += 64) {
            sb.append(base64, i, Math.min(i + 64, base64.length())).append("\n");
        }
        return sb.toString();
    }
}
