package com.gathin.launchos.tv;

/** A failed call, with a French message for the screen. It never carries the address, which holds the account. */
final class XtreamException extends Exception {
    enum Kind { BAD_ADDRESS, UNKNOWN_HOST, TIMEOUT, NETWORK, REFUSED, SERVER, BAD_ANSWER, AUTH, ACCOUNT_STATUS }

    final Kind kind;
    final int code;
    final String status;

    XtreamException(Kind kind, int code) {
        this(kind, code, null);
    }

    XtreamException(Kind kind, int code, String status) {
        super(kind + (code != 0 ? " " + code : ""));
        this.kind = kind;
        this.code = code;
        this.status = status;
    }

    String french() {
        switch (kind) {
            case BAD_ADDRESS:
                return "L'adresse du serveur n'est pas valide. Exemple : http://serveur.exemple:8080";
            case UNKNOWN_HOST:
                return "Adresse du serveur introuvable. Vérifie l'adresse et la connexion de la box.";
            case TIMEOUT:
                return "Le serveur ne répond pas (délai dépassé).";
            case NETWORK:
                return "Impossible de joindre le serveur.";
            case REFUSED:
                return "Le serveur refuse l'accès (code " + code + "). Identifiants, ou trop de connexions en même temps.";
            case SERVER:
                return "Le serveur a répondu avec une erreur (code " + code + ").";
            case BAD_ANSWER:
                return "Le serveur a envoyé une réponse illisible. Ce n'est peut-être pas un serveur Xtream Codes.";
            case AUTH:
                return "Identifiant ou mot de passe refusé par le serveur.";
            case ACCOUNT_STATUS:
                return "Le compte n'est pas actif (état : " + status + ").";
            default:
                return "Erreur inconnue.";
        }
    }
}
