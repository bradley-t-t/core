package com.core.plugin.command;

import com.core.plugin.modules.rank.RankLevel;
import org.bukkit.Material;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Metadata annotation for command classes. Access control is rank-based:
 * {@code minRank} declares the minimum {@link RankLevel} required. Inline
 * rank checks in command logic handle sub-feature gating (e.g., targeting
 * other players may require a higher rank).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface CommandInfo {

    String name();

    String[] aliases() default {};

    String description() default "";

    String usage() default "";

    /** Minimum rank level required to use this command. Defaults to {@link RankLevel#MEMBER}. */
    RankLevel minRank() default RankLevel.MEMBER;

    boolean playerOnly() default false;

    int minArgs() default 0;

    Material icon() default Material.PAPER;

    /** Hidden commands show "Unknown command" to unauthorized players and don't appear in /help. */
    boolean hidden() default false;
}
