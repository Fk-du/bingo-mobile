package com.bingo.app.master.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DepositAccountsCodecTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final DepositAccountsCodec codec = new DepositAccountsCodec(objectMapper);

    @Test
    void blankStorageMeansNoAccounts() {
        assertTrue(codec.read(null).isEmpty());
        assertTrue(codec.read("   ").isEmpty());
    }

    @Test
    void aJsonArrayBecomesStructuredAccounts() {
        List<DepositAccount> accounts = codec.read(
                "[{\"bank\":\"TeleBirr\",\"accountNumber\":\"0911234567\",\"ownerName\":\"Abebe\"},"
                        + "{\"bank\":\"CBE\",\"accountNumber\":\"1000123456789\"}]");
        assertEquals(2, accounts.size());
        assertEquals("TeleBirr", accounts.get(0).bank());
        assertEquals("0911234567", accounts.get(0).accountNumber());
        assertEquals("Abebe", accounts.get(0).ownerName());
        assertNull(accounts.get(1).ownerName());
    }

    @Test
    void legacyFreeTextIsKeptAsOneAccountNumber() {
        List<DepositAccount> accounts = codec.read("TeleBirr: 0911234567\nCBE: 1000987654321");
        assertEquals(1, accounts.size());
        assertNull(accounts.get(0).bank());
        assertEquals("TeleBirr: 0911234567\nCBE: 1000987654321", accounts.get(0).accountNumber());
        assertNull(accounts.get(0).ownerName());
    }

    @Test
    void malformedJsonFallsBackToLegacyText() {
        List<DepositAccount> accounts = codec.read("[{\"bank\": broken");
        assertEquals(1, accounts.size());
        assertEquals("[{\"bank\": broken", accounts.get(0).accountNumber());
    }

    @Test
    void writeThenReadRoundTrips() {
        List<DepositAccount> original = List.of(
                new DepositAccount("TeleBirr", "0911234567", "Abebe"),
                new DepositAccount("CBE", "  1000123456789 ", null));
        String stored = codec.write(original);

        List<DepositAccount> reread = codec.read(stored);
        assertEquals("1000123456789", reread.get(1).accountNumber());
        assertEquals("Abebe", reread.get(0).ownerName());
        assertNull(reread.get(1).ownerName());
    }
}