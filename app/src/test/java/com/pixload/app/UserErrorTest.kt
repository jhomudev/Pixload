package com.pixload.app

import org.junit.Assert.assertEquals
import org.junit.Test

class UserErrorTest {
    @Test fun rejectsUntrustedTechnicalDetails() {
        for (raw in listOf(null, "", "Pixiv respondió con HTTP 404", "Pixiv respondió con HTTP 403.",
            "No se pudo crear content://provider/secret.part", "No se pudo escribir /storage/private.txt",
            "Unexpected token at $.body", "https://i.pximg.net/secret", "Error: SocketException: failed")) {
            assertEquals(UserError.FALLBACK_ES, UserError.safeSpanish(raw))
        }
    }

    @Test fun keepsOnlyExactAppAuthoredActionableErrors() {
        assertEquals("Esta obra requiere iniciar sesión o no está disponible para tu cuenta.",
            UserError.safeSpanish("Esta obra requiere iniciar sesión o no está disponible para tu cuenta."))
        assertEquals("No se puede acceder a la carpeta elegida.",
            UserError.safeSpanish("No se puede acceder a la carpeta elegida."))
        assertEquals(UserError.FALLBACK_ES,
            UserError.safeSpanish("Esta obra requiere iniciar sesión o no está disponible para tu cuenta. /private"))
    }
}
