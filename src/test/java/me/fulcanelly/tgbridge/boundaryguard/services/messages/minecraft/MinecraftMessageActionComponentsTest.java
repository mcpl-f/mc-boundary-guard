package me.fulcanelly.tgbridge.boundaryguard.services.messages.minecraft;

import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import me.fulcanelly.tgbridge.boundaryguard.config.BoundaryGuardConfig;
import me.fulcanelly.tgbridge.boundaryguard.services.conditions.ConditionReason;
import me.fulcanelly.tgbridge.boundaryguard.services.conditions.ReasonExpr;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.hover.content.Text;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MinecraftMessageActionComponentsTest {

    private static BoundaryGuardConfig hardcodedCurrentMessageConfig() {
        return new BoundaryGuardConfig(
                YamlConfiguration.loadConfiguration(new StringReader("default-locale: ru\n")),
                Map.of(
                        "en", YamlConfiguration.loadConfiguration(new StringReader("""
                                join:
                                  before: "before"
                                reasons:
                                  tg-linking:
                                    label: "[Bind Telegram - /tg account register]"
                                    command: "/tg account register"
                                    hover: "Run /tg account register"
                                  playtime:
                                    label: "play longer on the server"
                                  return-to-spawn:
                                    label: "[Return to spawn - /tgspawn]"
                                    command: "/tgspawn"
                                    hover: "Run /tgspawn"
                                restrictions:
                                  leave-spawn:
                                    gerund: "leaving the spawn area"
                                  use-containers:
                                    gerund: "using containers"
                                """)),
                        "ru", YamlConfiguration.loadConfiguration(new StringReader("""
                                join:
                                  before: "перед тем как"
                                reasons:
                                  tg-linking:
                                    label: "[Привязать Telegram - /tg account register]"
                                    command: "/tg account register"
                                    hover: "Выполнить /tg account register"
                                  playtime:
                                    label: "играть дольше на сервере"
                                  return-to-spawn:
                                    label: "[Вернуться на спавн - /tgspawn]"
                                    command: "/tgspawn"
                                    hover: "Выполнить /tgspawn"
                                restrictions:
                                  leave-spawn:
                                    gerund: "выйти со спавна"
                                  use-containers:
                                    gerund: "пользоваться контейнерами"
                                """))));
    }

    private BaseComponent captureContainerMessage(
            BoundaryGuardConfig messageConfig, String locale, ConditionReason reason) {
        Player recipient = mock(Player.class);
        Player.Spigot recipientSpigot = mock(Player.Spigot.class);
        when(recipient.getUniqueId()).thenReturn(UUID.randomUUID());
        when(recipient.getLocale()).thenReturn(locale);
        when(recipient.spigot()).thenReturn(recipientSpigot);

        new MinecraftMessageService(messageConfig, 0L).sendContainerBlocked(
                recipient, Optional.of(new ReasonExpr.Leaf(reason)));

        ArgumentCaptor<BaseComponent> captor = ArgumentCaptor.forClass(BaseComponent.class);
        verify(recipientSpigot).sendMessage(captor.capture());
        return captor.getValue();
    }

    private BaseComponent captureBoundaryMessage(BoundaryGuardConfig messageConfig, String locale) {
        Player recipient = mock(Player.class);
        Player.Spigot recipientSpigot = mock(Player.Spigot.class);
        when(recipient.getUniqueId()).thenReturn(UUID.randomUUID());
        when(recipient.getLocale()).thenReturn(locale);
        when(recipient.spigot()).thenReturn(recipientSpigot);

        new MinecraftMessageService(messageConfig, 0L).sendBoundaryBlocked(
                recipient, Optional.of(new ReasonExpr.Leaf(ConditionReason.PLAYTIME)));

        ArgumentCaptor<BaseComponent> captor = ArgumentCaptor.forClass(BaseComponent.class);
        verify(recipientSpigot).sendMessage(captor.capture());
        return captor.getValue();
    }

    private static void assertAction(BaseComponent component, String command, String hoverText) {
      assertEquals(ChatColor.BLUE, component.getColor());
        assertEquals(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command), component.getClickEvent());
        HoverEvent hoverEvent = component.getHoverEvent();
        assertNotNull(hoverEvent);
        assertEquals(HoverEvent.Action.SHOW_TEXT, hoverEvent.getAction());
        Text hoverContent = assertInstanceOf(Text.class, hoverEvent.getContents().get(0));
        assertEquals(hoverText, hoverContent.getValue());
    }

    @Test
    void currentConfigBuildsTelegramAndReturnToSpawnActionsForEveryPlayerLocale() {
        BoundaryGuardConfig messageConfig = hardcodedCurrentMessageConfig();

        for (String locale : List.of("en_US", "ru_RU", "de_DE")) {
            boolean english = locale.startsWith("en");
            BaseComponent telegramRoot = captureContainerMessage(
                    messageConfig, locale, ConditionReason.TELEGRAM_LINKING);
            assertEquals(3, telegramRoot.getExtra().size());
            BaseComponent telegram = telegramRoot.getExtra().get(0);
            assertAction(telegram, "/tg account register",
                    english ? "Run /tg account register" : "Выполнить /tg account register");

            BaseComponent spawnReasonRoot = captureContainerMessage(
                    messageConfig, locale, ConditionReason.RETURN_TO_SPAWN);
            assertEquals(3, spawnReasonRoot.getExtra().size());
            BaseComponent returnToSpawn = spawnReasonRoot.getExtra().get(0);
            assertAction(returnToSpawn, "/tgspawn",
                    english ? "Run /tgspawn" : "Выполнить /tgspawn");
        }
    }

    @Test
    void currentConfigBuildsBoundaryReturnToSpawnActionWithClickAndHoverExtras() {
        BoundaryGuardConfig messageConfig = hardcodedCurrentMessageConfig();

        for (String locale : List.of("en_US", "ru_RU", "de_DE")) {
            BaseComponent root = captureBoundaryMessage(messageConfig, locale);
            assertEquals(5, root.getExtra().size());
            BaseComponent spawnButton = root.getExtra().get(4);
            assertAction(spawnButton, "/tgspawn",
                    locale.startsWith("en") ? "Run /tgspawn" : "Выполнить /tgspawn");
        }
    }

    @Test
    void clickableAvailabilityDoesNotChangeWithPlayerLocaleWhenTranslationsDiffer() {
        BoundaryGuardConfig messageConfig = new BoundaryGuardConfig(
                YamlConfiguration.loadConfiguration(new StringReader("default-locale: ru\n")),
                Map.of(
                        "ru", YamlConfiguration.loadConfiguration(new StringReader("""
                                join:
                                  before: "перед тем как"
                                reasons:
                                  tg-linking:
                                    label: "[Привязать Telegram - /tg account register]"
                                restrictions:
                                  use-containers:
                                    gerund: "пользоваться контейнерами"
                                """)),
                        "de", YamlConfiguration.loadConfiguration(new StringReader("""
                                join:
                                  before: "before"
                                reasons:
                                  tg-linking:
                                    label: "[Telegram verbinden - /tg account register]"
                                    command: "/tg account register"
                                    hover: "Run /tg account register"
                                restrictions:
                                  use-containers:
                                    gerund: "using containers"
                                """))));

        for (String locale : List.of("ru_RU", "de_DE")) {
            BaseComponent root = captureContainerMessage(messageConfig, locale, ConditionReason.TELEGRAM_LINKING);
            BaseComponent reason = root.getExtra().get(0);
            assertNull(reason.getClickEvent());
            assertNull(reason.getHoverEvent());
        }
    }
}
