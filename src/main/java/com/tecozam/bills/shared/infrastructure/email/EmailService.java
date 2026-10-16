package com.tecozam.bills.shared.infrastructure.email;

public interface EmailService {

    void enviarInvitacionAdmin(String email, String nombre, String link);
}
