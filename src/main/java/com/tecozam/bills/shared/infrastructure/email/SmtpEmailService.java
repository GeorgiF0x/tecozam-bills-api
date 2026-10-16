package com.tecozam.bills.shared.infrastructure.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class SmtpEmailService implements EmailService {

    private final JavaMailSender mailSender;
    private final String from;

    public SmtpEmailService(
            JavaMailSender mailSender,
            @Value("${app.mail.from:noreply@tecozam.com}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void enviarInvitacionAdmin(String email, String nombre, String link) {
        SimpleMailMessage mensaje = new SimpleMailMessage();
        mensaje.setFrom(from);
        mensaje.setTo(email);
        mensaje.setSubject("Tienes una invitación a Tecozam Bills Manager");
        mensaje.setText("""
                Hola %s,

                Has sido invitado a unirte a Tecozam Bills Manager como administrador.

                Para completar tu registro, accede al siguiente enlace:
                %s

                Si no esperabas esta invitación, puedes ignorar este mensaje.
                """.formatted(nombre, link));
        mailSender.send(mensaje);
        log.info("Invitación de administrador enviada a {}", email);
    }
}
