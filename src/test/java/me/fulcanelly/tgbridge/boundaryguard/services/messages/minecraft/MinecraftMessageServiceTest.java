package me.fulcanelly.tgbridge.boundaryguard.services.messages.minecraft;

import java.util.List;
import java.util.UUID;

import me.fulcanelly.tgbridge.boundaryguard.config.BoundaryGuardConfig;
import me.fulcanelly.tgbridge.boundaryguard.services.conditions.ConditionReason;

import net.md_5.bungee.api.ChatColor;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MinecraftMessageServiceTest {

    @Mock
    private BoundaryGuardConfig config;
    @Mock
    private Player player;

    @Test
    void repeatedWarningsAreSuppressedUntilTheCooldownElapses() {
        when(config.defaultLocale()).thenReturn("en");
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getLocale()).thenReturn("en_US");
        when(config.message("en", "container-blocked")).thenReturn("blocked");

        MinecraftMessageService messages = new MinecraftMessageService(config, 10_000L);

        messages.sendContainerBlocked(player);
        messages.sendContainerBlocked(player);

        verify(player, times(1)).sendMessage(ChatColor.RED + "blocked");
    }

    @Test
    void forgetResetsTheCooldownSoTheNextWarningIsSentImmediately() {
        when(config.defaultLocale()).thenReturn("en");
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getLocale()).thenReturn("en_US");
        when(config.message("en", "container-blocked")).thenReturn("blocked");

        MinecraftMessageService messages = new MinecraftMessageService(config, 10_000L);

        messages.sendContainerBlocked(player);
        messages.forget(player);
        messages.sendContainerBlocked(player);

        verify(player, times(2)).sendMessage(ChatColor.RED + "blocked");
    }

    @Test
    void conditionReminderJoinsMultipleReasonsIntoOneMessage() {
        when(config.defaultLocale()).thenReturn("en");
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getLocale()).thenReturn("en_US");
        when(config.message("en", "condition-tg-linking")).thenReturn("link telegram");
        when(config.message("en", "condition-playtime")).thenReturn("play longer");

        MinecraftMessageService messages = new MinecraftMessageService(config, 10_000L);

        messages.sendConditionReminder(player, List.of(ConditionReason.TELEGRAM_LINKING, ConditionReason.PLAYTIME));

        verify(player).sendMessage(ChatColor.RED + "link telegram\nplay longer");
    }

    @Test
    void conditionReminderSendsNothingForAnEmptyReasonList() {
        MinecraftMessageService messages = new MinecraftMessageService(config, 10_000L);

        messages.sendConditionReminder(player, List.<ConditionReason>of());

        verify(player, never()).sendMessage(anyString());
    }
}
