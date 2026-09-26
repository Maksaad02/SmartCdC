package com.recouvtech.recouvback.event;

/**
 * Publie quand une relance immediate vient d'etre creee ; l'e-mail part APRES le commit
 * (RelanceEmailListener), sur un autre thread, sans utilisateur authentifie.
 */
public record RelanceCreeeEvent(Long relanceId) {}
