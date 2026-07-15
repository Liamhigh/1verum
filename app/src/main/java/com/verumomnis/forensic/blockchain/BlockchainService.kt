package com.verumomnis.forensic.blockchain

import com.verumomnis.forensic.model.OtsAnchorResult
import com.verumomnis.forensic.model.OtsVerifyResult

/**
 * Abstraction over a blockchain anchoring service (OpenTimestamps → Bitcoin).
 *
 * Implementations must be deterministic in their digest computation and proof
 * assembly, but non-deterministic in calendar reachability (offline path must be
 * handled gracefully).
 */
interface BlockchainService {

    /** Anchor an evidence SHA-512 hash to the blockchain. */
    fun anchor(sha512Hex: String): OtsAnchorResult

    /** Verify a detached .ots proof (Base64). */
    fun verify(otsProofBase64: String): OtsVerifyResult

    /** Upgrade a pending proof to a Bitcoin-attested proof, if available. */
    fun upgrade(otsProofBase64: String): OtsAnchorResult
}
