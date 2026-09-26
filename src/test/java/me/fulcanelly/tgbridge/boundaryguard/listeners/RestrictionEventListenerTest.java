package me.fulcanelly.tgbridge.boundaryguard.listeners;

import java.util.List;

import me.fulcanelly.tgbridge.boundaryguard.integrations.tgbridge.TelegramLinkStatusService;
import me.fulcanelly.tgbridge.boundaryguard.services.conditions.ConditionReason;
import me.fulcanelly.tgbridge.boundaryguard.services.messages.minecraft.MinecraftMessageService;
import me.fulcanelly.tgbridge.boundaryguard.services.restrictions.RestrictionService;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for a live StackOverflowError: {@code CraftPlayer#setGameMode}
 * fires {@link PlayerGameModeChangeEvent} synchronously, before the mode is actually
 * applied, so a handler that reacted by calling {@code RestrictionService#refresh}
 * re-entered {@code AdventureModeRestriction}, saw the (still old) game mode, called
 * {@code setGameMode} again, and recursed forever. These handlers must only ever
 * query restriction state (never call {@code refresh}, which touches strategies).
 */
@ExtendWith(MockitoExtension.class)
class RestrictionEventListenerTest {

    @Mock
    private RestrictionService restrictions;
    @Mock
    private TelegramLinkStatusService telegramLinkStatus;
    @Mock
    private MinecraftMessageService messages;
    @Mock
    private Player player;

    private RestrictionEventListener listener;

    @BeforeEach
    void setUp() {
        // Not a field initializer: those run before MockitoExtension injects the
        // @Mock fields, which would leave `restrictions` null here.
        listener = new RestrictionEventListener(restrictions, telegramLinkStatus, messages);
    }

    @Test
    void gameModeChangeToAdventureNeverCallsRefresh() {
        PlayerGameModeChangeEvent event = mock(PlayerGameModeChangeEvent.class);
        when(event.getNewGameMode()).thenReturn(GameMode.ADVENTURE);
        when(event.getPlayer()).thenReturn(player);
        when(restrictions.isRestricted(player)).thenReturn(true);
        when(restrictions.unmetReasons(player)).thenReturn(List.of(ConditionReason.TELEGRAM_LINKING));

        listener.onGameModeChange(event);

        verify(restrictions, never()).refresh(any());
        verify(messages).sendConditionReminder(player, List.of(ConditionReason.TELEGRAM_LINKING));
    }

    @Test
    void gameModeChangeToSomethingElseIsIgnored() {
        PlayerGameModeChangeEvent event = mock(PlayerGameModeChangeEvent.class);
        when(event.getNewGameMode()).thenReturn(GameMode.SURVIVAL);

        listener.onGameModeChange(event);

        verify(restrictions, never()).isRestricted(any());
        verify(restrictions, never()).refresh(any());
    }

    @Test
    void armSwingWhileInAdventureModeNeverCallsRefresh() {
        PlayerAnimationEvent event = mock(PlayerAnimationEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(player.getGameMode()).thenReturn(GameMode.ADVENTURE);
        when(restrictions.isRestricted(player)).thenReturn(true);
        when(restrictions.unmetReasons(player)).thenReturn(List.of());

        listener.onArmSwing(event);

        verify(restrictions, never()).refresh(any());
    }

    @Test
    void armSwingOutsideAdventureModeIsIgnored() {
        PlayerAnimationEvent event = mock(PlayerAnimationEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);

        listener.onArmSwing(event);

        verify(restrictions, never()).isRestricted(any());
        verify(restrictions, never()).refresh(any());
    }

    @Test
    void blockPlaceOutsideAdventureModeIsIgnored() {
        BlockPlaceEvent event = mock(BlockPlaceEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);

        listener.onBlockPlace(event);

        verify(restrictions, never()).isRestricted(any());
        verify(restrictions, never()).refresh(any());
    }
}
