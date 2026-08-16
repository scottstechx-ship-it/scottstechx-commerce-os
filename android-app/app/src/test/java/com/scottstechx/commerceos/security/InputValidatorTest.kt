package com.scottstechx.commerceos.security

import org.junit.Assert.assertTrue
import org.junit.Test

class InputValidatorTest {

    @Test fun `title - valid`() {
        val r = InputValidator.validateTitle("Bark cloth tote bag")
        assertTrue(r is InputValidator.Result.Ok)
    }

    @Test fun `title - blank rejected`() {
        val r = InputValidator.validateTitle("   ")
        assertTrue(r is InputValidator.Result.Invalid)
    }

    @Test fun `title - too long rejected`() {
        val r = InputValidator.validateTitle("x".repeat(100))
        assertTrue(r is InputValidator.Result.Invalid)
    }

    @Test fun `description - valid`() {
        val r = InputValidator.validateDescription("Handmade in Kampala.")
        assertTrue(r is InputValidator.Result.Ok)
    }

    @Test fun `description - too long rejected`() {
        val r = InputValidator.validateDescription("x".repeat(600))
        assertTrue(r is InputValidator.Result.Invalid)
    }

    @Test fun `price - zero accepted`() {
        val r = InputValidator.validatePriceMinor(0L)
        assertTrue(r is InputValidator.Result.Ok)
    }

    @Test fun `price - negative rejected`() {
        val r = InputValidator.validatePriceMinor(-1L)
        assertTrue(r is InputValidator.Result.Invalid)
    }

    @Test fun `price - over upper bound rejected`() {
        val r = InputValidator.validatePriceMinor(10_000_000_000L)
        assertTrue(r is InputValidator.Result.Invalid)
    }

    @Test fun `stock - zero accepted`() {
        val r = InputValidator.validateStock(0)
        assertTrue(r is InputValidator.Result.Ok)
    }

    @Test fun `stock - negative rejected`() {
        val r = InputValidator.validateStock(-3)
        assertTrue(r is InputValidator.Result.Invalid)
    }

    @Test fun `phone - too short rejected`() {
        val r = InputValidator.validatePhone("123")
        assertTrue(r is InputValidator.Result.Invalid)
    }

    @Test fun `phone - with country code accepted`() {
        val r = InputValidator.validatePhone("+256700000000")
        assertTrue(r is InputValidator.Result.Ok)
    }

    @Test fun `phone - with dashes accepted`() {
        val r = InputValidator.validatePhone("0700-000-000")
        assertTrue(r is InputValidator.Result.Ok)
    }

    @Test fun `phone - invalid characters rejected`() {
        val r = InputValidator.validatePhone("0700000abc")
        assertTrue(r is InputValidator.Result.Invalid)
    }
}
