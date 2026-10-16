package com.tecozam.bills.shared.infrastructure.email;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SmtpEmailServiceTest {

    @Mock
    JavaMailSender mailSender;

    @Test
    @DisplayName("enviarInvitacionAdmin manda al destinatario correcto con el link en el cuerpo")
    void enviarInvitacionAdmin_mandaAlDestinatarioConLinkEnCuerpo() {
        SmtpEmailService service = new SmtpEmailService(mailSender, "noreply@tecozam.com");

        service.enviarInvitacionAdmin(
                "ana@tecozam.com",
                "Ana García",
                "https://app.tecozam.com/invitacion/abc123");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        SimpleMailMessage mensaje = captor.getValue();
        assertThat(mensaje.getTo()).containsExactly("ana@tecozam.com");
        assertThat(mensaje.getFrom()).isEqualTo("noreply@tecozam.com");
        assertThat(mensaje.getSubject()).contains("invitación");
        assertThat(mensaje.getText())
                .contains("Ana García")
                .contains("https://app.tecozam.com/invitacion/abc123");
    }

    @Test
    @DisplayName("enviarInvitacionAdmin con otro destinatario y link genera un cuerpo distinto (no hardcodeado)")
    void enviarInvitacionAdmin_conOtrosDatos_generaCuerpoDistinto() {
        SmtpEmailService service = new SmtpEmailService(mailSender, "noreply@tecozam.com");

        service.enviarInvitacionAdmin(
                "luis@tecozam.com",
                "Luis Pérez",
                "https://app.tecozam.com/invitacion/xyz789");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        SimpleMailMessage mensaje = captor.getValue();
        assertThat(mensaje.getTo()).containsExactly("luis@tecozam.com");
        assertThat(mensaje.getText())
                .contains("Luis Pérez")
                .contains("https://app.tecozam.com/invitacion/xyz789")
                .doesNotContain("Ana García")
                .doesNotContain("abc123");
    }
}
