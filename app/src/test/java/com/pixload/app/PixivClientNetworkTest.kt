package com.pixload.app

import android.net.Network
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.HttpURLConnection

class PixivClientNetworkTest {
    @Test fun transferHasNetworkBoundEntryPointAndKeepsExistingDefaultOpen() {
        assertEquals(HttpURLConnection::class.java,
            PixivClient::class.java.getMethod("open", String::class.java).returnType)
        assertEquals(HttpURLConnection::class.java,
            PixivClient::class.java.getMethod("open", String::class.java, Network::class.java).returnType)
    }
}
