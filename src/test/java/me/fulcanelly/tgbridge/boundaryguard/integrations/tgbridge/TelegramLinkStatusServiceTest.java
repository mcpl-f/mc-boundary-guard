package me.fulcanelly.tgbridge.boundaryguard.integrations.tgbridge;

import java.util.Optional;
import java.util.UUID;

import me.fulcanelly.tgbridge.tools.twofactor.register.SignupLoginReception;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelegramLinkStatusServiceTest {

    @Mock
    private SignupLoginReception reception;
    @Mock
    private Player player;

    @Test
    void positiveResultStaysCachedForTheRestOfTheSession() {
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn("alice");
        when(reception.getTgByUser("alice")).thenReturn(Optional.of(1L));

        TelegramLinkStatusService service = new TelegramLinkStatusService(reception, 1000L);

        assertTrue(service.isLinked(player));
        assertTrue(service.isLinked(player));

        verify(reception, times(1)).getTgByUser("alice");
    }

    @Test
    void negativeResultIsCachedOnlyWithinTheConfiguredWindow() {
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn("carl");
        when(reception.getTgByUser("carl")).thenReturn(Optional.empty());

        TelegramLinkStatusService service = new TelegramLinkStatusService(reception, 5000L);

        assertFalse(service.isLinked(player));
        assertFalse(service.isLinked(player));

        verify(reception, times(1)).getTgByUser("carl");
    }

    @Test
    void forgetClearsThePositiveCacheSoTheNextLoginIsRecheckedAgainstReception() {
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn("bob");
        when(reception.getTgByUser("bob")).thenReturn(Optional.of(1L));

        TelegramLinkStatusService service = new TelegramLinkStatusService(reception, 1000L);
        assertTrue(service.isLinked(player));

        service.forget(player);

        assertTrue(service.isLinked(player));
        verify(reception, times(2)).getTgByUser("bob");
    }
}
