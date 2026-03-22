package com.core.plugin;

import com.core.plugin.command.BaseCommand;
import com.core.plugin.command.CommandRegistry;
import com.core.plugin.commands.admin.HelpCommand;
import com.core.plugin.data.DataManager;
import com.core.plugin.listener.GuiListener;
import com.core.plugin.lang.Lang;
import com.core.plugin.lang.LanguageManager;
import com.core.plugin.service.Service;
import com.core.plugin.service.ServiceRegistry;
import com.core.plugin.util.ClassUtil;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

/**
 * Core plugin entry point. Services, commands, and listeners are auto-discovered
 * via classpath scanning and instantiated reflectively. All services must implement
 * {@link Service} with a {@code (CorePlugin)} constructor. All commands must extend
 * {@link BaseCommand} with a {@code (CorePlugin)} constructor. All listeners must
 * implement {@link Listener} with a {@code (CorePlugin)} constructor.
 */
public final class CorePlugin extends JavaPlugin {

    private static final String BASE_PACKAGE = "com.core.plugin";
    private static final String COMMAND_PACKAGE = "com.core.plugin.commands";
    private static final String LISTENER_PACKAGE = "com.core.plugin.listener";

    private DataManager dataManager;
    private LanguageManager languageManager;
    private ServiceRegistry serviceRegistry;
    private GuiListener guiListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        dataManager = new DataManager(this);
        languageManager = new LanguageManager(this);
        Lang.init(languageManager);

        serviceRegistry = new ServiceRegistry();
        guiListener = new GuiListener();

        registerServices();
        com.core.plugin.util.PlayerUtil.init(serviceRegistry);
        registerCommands();
        registerListeners();

        getLogger().info("Core enabled.");
    }

    @Override
    public void onDisable() {
        serviceRegistry.disableAll();
        getLogger().info("Core disabled.");
    }

    public DataManager dataManager() { return dataManager; }

    public ServiceRegistry services() { return serviceRegistry; }

    public GuiListener guiListener() { return guiListener; }

    public void reloadLanguage() {
        languageManager.reload();
    }

    @SuppressWarnings("unchecked")
    private void registerServices() {
        ClassLoader cl = getClassLoader();

        List<Class<? extends Service>> serviceClasses =
                ClassUtil.findSubclasses(BASE_PACKAGE, Service.class, cl);

        for (Class<? extends Service> serviceClass : serviceClasses) {
            try {
                Constructor<? extends Service> constructor = serviceClass.getConstructor(CorePlugin.class);
                Service service = constructor.newInstance(this);
                serviceRegistry.register((Class<Service>) serviceClass, service);
                getLogger().info("Registered service: " + serviceClass.getSimpleName());
            } catch (Exception e) {
                getLogger().severe("Failed to register service: " + serviceClass.getSimpleName());
                e.printStackTrace();
            }
        }
    }

    private void registerCommands() {
        var registry = new CommandRegistry(this);
        ClassLoader cl = getClassLoader();

        List<Class<? extends BaseCommand>> commandClasses =
                ClassUtil.findSubclasses(COMMAND_PACKAGE, BaseCommand.class, cl);

        HelpCommand helpCommand = null;
        List<BaseCommand> allCommands = new ArrayList<>();

        for (Class<? extends BaseCommand> commandClass : commandClasses) {
            try {
                Constructor<? extends BaseCommand> constructor = commandClass.getConstructor(CorePlugin.class);
                BaseCommand command = constructor.newInstance(this);
                allCommands.add(command);

                if (command instanceof HelpCommand help) {
                    helpCommand = help;
                }
            } catch (Exception e) {
                getLogger().severe("Failed to register command: " + commandClass.getSimpleName());
                e.printStackTrace();
            }
        }

        registry.registerAll(allCommands.toArray(BaseCommand[]::new));

        if (helpCommand != null) {
            helpCommand.setRegisteredCommands(allCommands);
        }

        getLogger().info("Registered " + allCommands.size() + " commands.");
    }

    private void registerListeners() {
        var pm = getServer().getPluginManager();
        ClassLoader cl = getClassLoader();

        List<Class<? extends Listener>> listenerClasses =
                ClassUtil.findSubclasses(BASE_PACKAGE, Listener.class, cl);

        for (Class<? extends Listener> listenerClass : listenerClasses) {
            try {
                Constructor<? extends Listener> constructor = listenerClass.getConstructor(CorePlugin.class);
                Listener listener = constructor.newInstance(this);
                pm.registerEvents(listener, this);
                getLogger().info("Registered listener: " + listenerClass.getSimpleName());
            } catch (NoSuchMethodException ignored) {
                // Skip listeners without a (CorePlugin) constructor — they're registered manually
            } catch (Exception e) {
                getLogger().severe("Failed to register listener: " + listenerClass.getSimpleName());
                e.printStackTrace();
            }
        }

        // GuiListener is special — no CorePlugin constructor, managed directly
        pm.registerEvents(guiListener, this);
    }
}
