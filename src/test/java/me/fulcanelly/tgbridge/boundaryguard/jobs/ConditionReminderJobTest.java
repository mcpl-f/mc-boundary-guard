package me.fulcanelly.tgbridge.boundaryguard.jobs;

import java.util.List;
import java.util.Optional;

import me.fulcanelly.tgbridge.boundaryguard.services.conditions.ConditionReason;
import me.fulcanelly.tgbridge.boundaryguard.services.conditions.ReasonExpr;
import me.fulcanelly.tgbridge.boundaryguard.services.messages.minecraft.MinecraftMessageService;
import me.fulcanelly.tgbridge.boundaryguard.services.restrictions.RestrictionEffect;
import me.fulcanelly.tgbridge.boundaryguard.services.restrictions.RestrictionService;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConditionReminderJobTest {

    @Mock
    private RestrictionService restrictions;
    @Mock
    private MinecraftMessageService messages;
    @Mock
    private Player restrictedPlayer;
    @Mock
    private Player freePlayer;

    @Test
    void remindsOnlyRestrictedOnlinePlayersAndNeverCallsRefresh() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(restrictedPlayer, freePlayer));

            when(restrictions.isRestricted(restrictedPlayer)).thenReturn(true);
            when(restrictions.isRestricted(freePlayer)).thenReturn(false);
            Optional<ReasonExpr> reason = Optional.of(new ReasonExpr.Leaf(ConditionReason.TELEGRAM_LINKING));
            when(restrictions.unmetReason(restrictedPlayer)).thenReturn(reason);
            List<RestrictionEffect> effects = List.of(RestrictionEffect.LEAVE_SPAWN, RestrictionEffect.USE_CONTAINERS);
            when(restrictions.activeEffects()).thenReturn(effects);

            new ConditionReminderJob(restrictions, messages).run();

            // Every active effect, not just one - this job isn't tied to any
            // specific action, unlike RestrictionEventListener's reminders.
            verify(messages).sendConditionReminder(restrictedPlayer, reason, effects);
            verify(messages, never()).sendConditionReminder(eq(freePlayer), any(), any());
            // Purely a notification job - it must never apply a Restriction
            // strategy's side effects, only RestrictionRefreshJob does that.
            verify(restrictions, never()).refresh(any());
        }
    }
}
