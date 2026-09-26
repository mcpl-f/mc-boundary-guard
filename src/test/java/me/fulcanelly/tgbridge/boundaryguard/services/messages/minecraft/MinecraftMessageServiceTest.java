package me.fulcanelly.tgbridge.boundaryguard.services.messages.minecraft;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import me.fulcanelly.tgbridge.boundaryguard.config.BoundaryGuardConfig;
import me.fulcanelly.tgbridge.boundaryguard.services.conditions.ConditionApplier;
import me.fulcanelly.tgbridge.boundaryguard.services.conditions.ConditionReason;
import me.fulcanelly.tgbridge.boundaryguard.services.conditions.ReasonExpr;
import me.fulcanelly.tgbridge.boundaryguard.services.restrictions.RestrictionEffect;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
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
    @Mock
    private Player.Spigot spigot;

    private void stubPlayer() {
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getLocale()).thenReturn("en_US");
        when(player.spigot()).thenReturn(spigot);
    }

    private BaseComponent sentComponent() {
        ArgumentCaptor<BaseComponent> captor = ArgumentCaptor.forClass(BaseComponent.class);
        verify(spigot).sendMessage(captor.capture());
        return captor.getValue();
    }

    // toLegacyText embeds this codebase's raw-string color tricks (ChatColor.X +
    // text) as literal characters, same as it would a real .setColor() call - strip
    // them so assertions read the composed sentence, not its formatting codes.
    private static String plainText(BaseComponent component) {
        return BaseComponent.toLegacyText(component).replaceAll("§.", "");
    }

    private void stubPlaytimeReason() {
        when(config.message("en", "reasons.playtime.label")).thenReturn("play longer");
        when(config.hasMessage("en", "reasons.playtime.command")).thenReturn(false);
    }

    // sendBoundaryBlocked/sendContainerBlocked use the bare "before" connector,
    // which needs a gerund right after it ("before leaving the spawn area").
    private void stubBeforeJoin() {
        when(config.message("en", "join.before")).thenReturn("before");
    }

    // sendConditionReminder uses "so that you can", which needs a bare
    // infinitive right after it ("so that you can leave the spawn area").
    private void stubSoThatJoin() {
        when(config.message("en", "join.so-that")).thenReturn("so that you can");
    }

    private void stubSpawnEscapeHatch() {
        when(config.message("en", "spawn-button")).thenReturn("[Teleport to spawn - /tgspawn]");
        when(config.message("en", "spawn-hover")).thenReturn("Run /tgspawn");
    }

    private void stubReturnToSpawnReason() {
        when(config.message("en", "reasons.return-to-spawn.label")).thenReturn("[Return to spawn - /tgspawn]");
        when(config.hasMessage("en", "reasons.return-to-spawn.command")).thenReturn(true);
        when(config.message("en", "reasons.return-to-spawn.command")).thenReturn("/tgspawn");
        when(config.message("en", "reasons.return-to-spawn.hover")).thenReturn("Run /tgspawn");
    }

    @Test
    void containerBlockedComposesTheReasonBeforeTheRestriction() {
        when(config.defaultLocale()).thenReturn("en");
        stubPlayer();
        stubPlaytimeReason();
        stubBeforeJoin();
        when(config.message("en", "restrictions.use-containers.gerund")).thenReturn("using containers");

        MinecraftMessageService messages = new MinecraftMessageService(config, 10_000L);
        messages.sendContainerBlocked(player, Optional.of(new ReasonExpr.Leaf(ConditionReason.PLAYTIME)));

        assertEquals("play longer before using containers", plainText(sentComponent()));
    }

    @Test
    void conditionReminderUsesSoThatInsteadOfBefore() {
        when(config.defaultLocale()).thenReturn("en");
        stubPlayer();
        stubPlaytimeReason();
        stubSoThatJoin();
        when(config.message("en", "restrictions.interact-freely.infinitive")).thenReturn("interact with the world freely");

        MinecraftMessageService messages = new MinecraftMessageService(config, 10_000L);
        messages.sendConditionReminder(
                player, Optional.of(new ReasonExpr.Leaf(ConditionReason.PLAYTIME)),
                List.of(RestrictionEffect.INTERACT_FREELY));

        assertEquals("play longer so that you can interact with the world freely", plainText(sentComponent()));
    }

    // The idle-player job (unlike an event tied to one specific action) passes
    // every currently-active effect, not just one - they're joined with "and"
    // since they're all simultaneously true, never alternatives.
    @Test
    void conditionReminderJoinsMultipleActiveEffectsWithAnd() {
        when(config.defaultLocale()).thenReturn("en");
        stubPlayer();
        stubPlaytimeReason();
        stubSoThatJoin();
        when(config.message("en", "join.and")).thenReturn("and");
        when(config.message("en", "restrictions.leave-spawn.infinitive")).thenReturn("leave the spawn area");
        when(config.message("en", "restrictions.use-containers.infinitive")).thenReturn("use containers");

        MinecraftMessageService messages = new MinecraftMessageService(config, 10_000L);
        messages.sendConditionReminder(
                player, Optional.of(new ReasonExpr.Leaf(ConditionReason.PLAYTIME)),
                List.of(RestrictionEffect.LEAVE_SPAWN, RestrictionEffect.USE_CONTAINERS));

        BaseComponent root = sentComponent();
        assertEquals(
                "play longer so that you can leave the spawn area and use containers",
            plainText(root));
        BaseComponent joinedEffects = root.getExtra().get(2);
        assertEquals(ChatColor.YELLOW, joinedEffects.getExtra().get(0).getColor());
        assertEquals(ChatColor.YELLOW, joinedEffects.getExtra().get(1).getColor());
        assertEquals(ChatColor.YELLOW, joinedEffects.getExtra().get(2).getColor());
    }

    @Test
    void clickableReasonShowsTheCommandAsTextAndCarriesClickAndHoverEvents() {
        when(config.defaultLocale()).thenReturn("en");
        stubPlayer();
        when(config.message("en", "reasons.tg-linking.label")).thenReturn("[Bind Telegram - /tg account register]");
        when(config.hasMessage("en", "reasons.tg-linking.command")).thenReturn(true);
        when(config.message("en", "reasons.tg-linking.command")).thenReturn("/tg account register");
        when(config.message("en", "reasons.tg-linking.hover")).thenReturn("Run /tg account register");
        stubBeforeJoin();
        when(config.message("en", "restrictions.use-containers.gerund")).thenReturn("using containers");

        MinecraftMessageService messages = new MinecraftMessageService(config, 10_000L);
        messages.sendContainerBlocked(player, Optional.of(new ReasonExpr.Leaf(ConditionReason.TELEGRAM_LINKING)));

        BaseComponent root = sentComponent();
        // Old-client fallback: the runnable command is visible as plain text too,
        // not hidden behind a click-only action.
        assertEquals(
                "[Bind Telegram - /tg account register] before using containers",
                plainText(root));

        BaseComponent reasonComponent = root.getExtra().get(0);
        assertEquals(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tg account register"),
                reasonComponent.getClickEvent());
        assertNotNull(reasonComponent.getHoverEvent());
        assertEquals(ChatColor.BLUE, reasonComponent.getColor());
    }

    @Test
    void reasonWithoutACommandHasNoClickEvent() {
        when(config.defaultLocale()).thenReturn("en");
        stubPlayer();
        stubPlaytimeReason();
        stubBeforeJoin();
        when(config.message("en", "restrictions.use-containers.gerund")).thenReturn("using containers");

        MinecraftMessageService messages = new MinecraftMessageService(config, 10_000L);
        messages.sendContainerBlocked(player, Optional.of(new ReasonExpr.Leaf(ConditionReason.PLAYTIME)));

        BaseComponent reasonComponent = sentComponent().getExtra().get(0);
        assertNull(reasonComponent.getClickEvent());
    }

    @Test
    void groupOfAllIsJoinedWithAnd() {
        when(config.defaultLocale()).thenReturn("en");
        stubPlayer();
        when(config.message("en", "reasons.tg-linking.label")).thenReturn("link telegram");
        when(config.hasMessage("en", "reasons.tg-linking.command")).thenReturn(false);
        stubPlaytimeReason();
        when(config.message("en", "join.and")).thenReturn("and");
        stubBeforeJoin();
        when(config.message("en", "restrictions.use-containers.gerund")).thenReturn("using containers");

        ReasonExpr reason = new ReasonExpr.Group(ConditionApplier.Operator.ALL, List.of(
                new ReasonExpr.Leaf(ConditionReason.TELEGRAM_LINKING),
                new ReasonExpr.Leaf(ConditionReason.PLAYTIME)));

        MinecraftMessageService messages = new MinecraftMessageService(config, 10_000L);
        messages.sendContainerBlocked(player, Optional.of(reason));

        assertEquals(
                "link telegram and play longer before using containers",
                plainText(sentComponent()));
    }

    @Test
    void groupOfAnyIsJoinedWithOr() {
        when(config.defaultLocale()).thenReturn("en");
        stubPlayer();
        when(config.message("en", "reasons.tg-linking.label")).thenReturn("link telegram");
        when(config.hasMessage("en", "reasons.tg-linking.command")).thenReturn(false);
        stubPlaytimeReason();
        when(config.message("en", "join.or")).thenReturn("or");
        stubBeforeJoin();
        when(config.message("en", "restrictions.use-containers.gerund")).thenReturn("using containers");

        ReasonExpr reason = new ReasonExpr.Group(ConditionApplier.Operator.ANY, List.of(
                new ReasonExpr.Leaf(ConditionReason.TELEGRAM_LINKING),
                new ReasonExpr.Leaf(ConditionReason.PLAYTIME)));

        MinecraftMessageService messages = new MinecraftMessageService(config, 10_000L);
        messages.sendContainerBlocked(player, Optional.of(reason));

        assertEquals(
                "link telegram or play longer before using containers",
                plainText(sentComponent()));
    }

    @Test
    void boundaryBlockedUsesTheLeaveSpawnRestrictionAndAppendsTheSpawnEscapeHatch() {
        when(config.defaultLocale()).thenReturn("en");
        stubPlayer();
        stubPlaytimeReason();
        stubBeforeJoin();
        when(config.message("en", "restrictions.leave-spawn.gerund")).thenReturn("leaving the spawn area");
        stubSpawnEscapeHatch();

        MinecraftMessageService messages = new MinecraftMessageService(config, 10_000L);
        messages.sendBoundaryBlocked(player, Optional.of(new ReasonExpr.Leaf(ConditionReason.PLAYTIME)));

        assertEquals(
                "play longer before leaving the spawn area [Teleport to spawn - /tgspawn]",
                plainText(sentComponent()));
    }

    // /tgspawn is not a reason competing with e.g. Telegram linking in the
    // composed sentence - walking/teleporting back doesn't satisfy
    // rules.condition the way linking does, it just gets a player out of the
    // restricted area. So it's always appended as its own separate, clickable
    // action, regardless of which reason(s) triggered the block.
    @Test
    void boundaryBlockedAlwaysAppendsAClickableTgspawnEscapeHatch() {
        when(config.defaultLocale()).thenReturn("en");
        stubPlayer();
        stubPlaytimeReason();
        stubBeforeJoin();
        when(config.message("en", "restrictions.leave-spawn.gerund")).thenReturn("leaving the spawn area");
        stubSpawnEscapeHatch();

        MinecraftMessageService messages = new MinecraftMessageService(config, 10_000L);
        messages.sendBoundaryBlocked(player, Optional.of(new ReasonExpr.Leaf(ConditionReason.PLAYTIME)));

        BaseComponent root = sentComponent();
        BaseComponent spawnComponent = root.getExtra().get(root.getExtra().size() - 1);
        assertEquals(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tgspawn"), spawnComponent.getClickEvent());
        assertNotNull(spawnComponent.getHoverEvent());
        assertEquals(ChatColor.BLUE, spawnComponent.getColor());
    }

    // RETURN_TO_SPAWN (InSpawnRadiusCondition) isn't durable the way linking or
    // playtime are, but it's still a genuine rules.condition alternative - and
    // unlike the leave-spawn message, there's nothing circular about offering
    // it here: "return to spawn" is a perfectly good answer to "you can't use
    // containers".
    @Test
    void returnToSpawnReasonIsShownAndClickableWhenNotDescribingTheLeaveSpawnRestriction() {
        when(config.defaultLocale()).thenReturn("en");
        stubPlayer();
        stubReturnToSpawnReason();
        stubBeforeJoin();
        when(config.message("en", "restrictions.use-containers.gerund")).thenReturn("using containers");

        MinecraftMessageService messages = new MinecraftMessageService(config, 10_000L);
        messages.sendContainerBlocked(player, Optional.of(new ReasonExpr.Leaf(ConditionReason.RETURN_TO_SPAWN)));

        BaseComponent root = sentComponent();
        assertEquals("[Return to spawn - /tgspawn] before using containers", plainText(root));
        assertEquals(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tgspawn"), root.getExtra().get(0).getClickEvent());
        assertEquals(ChatColor.BLUE, root.getExtra().get(0).getColor());
    }

    // The exact bug this guards against: RETURN_TO_SPAWN must never be offered
    // as the fix for the leave-spawn restriction itself ("return to spawn,
    // before leaving the spawn area" is circular), even when it's genuinely
    // part of the unmet reason alongside something else. Collapses the
    // now-single-child group down to just the remaining reason, same as if
    // RETURN_TO_SPAWN had never been unmet at all.
    @Test
    void boundaryBlockedNeverMentionsReturnToSpawnEvenWhenItsPartOfTheUnmetReason() {
        when(config.defaultLocale()).thenReturn("en");
        stubPlayer();
        when(config.message("en", "reasons.tg-linking.label")).thenReturn("[Bind Telegram - /tg account register]");
        when(config.hasMessage("en", "reasons.tg-linking.command")).thenReturn(false);
        stubBeforeJoin();
        when(config.message("en", "restrictions.leave-spawn.gerund")).thenReturn("leaving the spawn area");
        stubSpawnEscapeHatch();

        ReasonExpr reason = new ReasonExpr.Group(ConditionApplier.Operator.ANY, List.of(
                new ReasonExpr.Leaf(ConditionReason.TELEGRAM_LINKING),
                new ReasonExpr.Leaf(ConditionReason.RETURN_TO_SPAWN)));

        MinecraftMessageService messages = new MinecraftMessageService(config, 10_000L);
        messages.sendBoundaryBlocked(player, Optional.of(reason));

        assertEquals(
                "[Bind Telegram - /tg account register] before leaving the spawn area [Teleport to spawn - /tgspawn]",
                plainText(sentComponent()));
    }

    // If in-spawn-radius were configured as the *only* rules.condition branch,
    // dropping RETURN_TO_SPAWN would leave nothing to say - falls back to
    // showing it anyway rather than silently sending no reason at all.
    @Test
    void boundaryBlockedFallsBackToReturnToSpawnWhenItsTheOnlyUnmetReason() {
        when(config.defaultLocale()).thenReturn("en");
        stubPlayer();
        stubReturnToSpawnReason();
        stubBeforeJoin();
        when(config.message("en", "restrictions.leave-spawn.gerund")).thenReturn("leaving the spawn area");
        stubSpawnEscapeHatch();

        MinecraftMessageService messages = new MinecraftMessageService(config, 10_000L);
        messages.sendBoundaryBlocked(player, Optional.of(new ReasonExpr.Leaf(ConditionReason.RETURN_TO_SPAWN)));

        assertEquals(
                "[Return to spawn - /tgspawn] before leaving the spawn area [Teleport to spawn - /tgspawn]",
                plainText(sentComponent()));
    }

    // Colors must be assigned structurally to the intended components, not
    // merely appear in toLegacyText() after legacy formatting is stripped.
    @Test
    void restrictionPhraseInheritsRedFromTheMessageRootInsteadOfStayingUncolored() {
        when(config.defaultLocale()).thenReturn("en");
        stubPlayer();
        stubPlaytimeReason();
        stubBeforeJoin();
        when(config.message("en", "restrictions.use-containers.gerund")).thenReturn("using containers");

        MinecraftMessageService messages = new MinecraftMessageService(config, 10_000L);
        messages.sendContainerBlocked(player, Optional.of(new ReasonExpr.Leaf(ConditionReason.PLAYTIME)));

        BaseComponent root = sentComponent();
        assertEquals(ChatColor.RED, root.getColor());
        assertEquals(ChatColor.YELLOW, root.getExtra().get(1).getColor());

        BaseComponent effects = root.getExtra().get(root.getExtra().size() - 1);
        BaseComponent restrictionPhrase = effects.getExtra().get(0);
        assertEquals(ChatColor.YELLOW, restrictionPhrase.getColor());
    }

    @Test
    void clickableReasonIsBlueWhileConnectorsAreYellowAndTheWarningRootStaysRed() {
        when(config.defaultLocale()).thenReturn("en");
        stubPlayer();
        when(config.message("en", "reasons.tg-linking.label")).thenReturn("[Bind Telegram - /tg account register]");
        when(config.hasMessage("en", "reasons.tg-linking.command")).thenReturn(true);
        when(config.message("en", "reasons.tg-linking.command")).thenReturn("/tg account register");
        when(config.message("en", "reasons.tg-linking.hover")).thenReturn("Run /tg account register");
        stubBeforeJoin();
        when(config.message("en", "restrictions.use-containers.gerund")).thenReturn("using containers");

        MinecraftMessageService messages = new MinecraftMessageService(config, 10_000L);
        messages.sendContainerBlocked(player, Optional.of(new ReasonExpr.Leaf(ConditionReason.TELEGRAM_LINKING)));

        BaseComponent root = sentComponent();
        assertEquals(ChatColor.RED, root.getColor());
        assertEquals(ChatColor.BLUE, root.getExtra().get(0).getColor());
        assertEquals(ChatColor.YELLOW, root.getExtra().get(1).getColor());
    }

    @Test
    void sendsNothingWhenReasonIsEmpty() {
        MinecraftMessageService messages = new MinecraftMessageService(config, 10_000L);

        messages.sendContainerBlocked(player, Optional.empty());
        messages.sendBoundaryBlocked(player, Optional.empty());
        messages.sendConditionReminder(player, Optional.empty(), List.of(RestrictionEffect.INTERACT_FREELY));

        verify(player, never()).spigot();
    }

    @Test
    void conditionReminderSendsNothingWhenEffectsListIsEmptyEvenWithAReason() {
        MinecraftMessageService messages = new MinecraftMessageService(config, 10_000L);

        messages.sendConditionReminder(
                player, Optional.of(new ReasonExpr.Leaf(ConditionReason.PLAYTIME)), List.of());

        verify(player, never()).spigot();
    }

    @Test
    void repeatedWarningsAreSuppressedUntilTheCooldownElapses() {
        when(config.defaultLocale()).thenReturn("en");
        stubPlayer();
        stubPlaytimeReason();
        stubBeforeJoin();
        when(config.message("en", "restrictions.use-containers.gerund")).thenReturn("using containers");

        MinecraftMessageService messages = new MinecraftMessageService(config, 10_000L);
        Optional<ReasonExpr> reason = Optional.of(new ReasonExpr.Leaf(ConditionReason.PLAYTIME));

        messages.sendContainerBlocked(player, reason);
        messages.sendContainerBlocked(player, reason);

        verify(spigot, times(1)).sendMessage(any(BaseComponent.class));
    }

    @Test
    void forgetResetsTheCooldownSoTheNextWarningIsSentImmediately() {
        when(config.defaultLocale()).thenReturn("en");
        stubPlayer();
        stubPlaytimeReason();
        stubBeforeJoin();
        when(config.message("en", "restrictions.use-containers.gerund")).thenReturn("using containers");

        MinecraftMessageService messages = new MinecraftMessageService(config, 10_000L);
        Optional<ReasonExpr> reason = Optional.of(new ReasonExpr.Leaf(ConditionReason.PLAYTIME));

        messages.sendContainerBlocked(player, reason);
        messages.forget(player);
        messages.sendContainerBlocked(player, reason);

        verify(spigot, times(2)).sendMessage(any(BaseComponent.class));
    }
}
