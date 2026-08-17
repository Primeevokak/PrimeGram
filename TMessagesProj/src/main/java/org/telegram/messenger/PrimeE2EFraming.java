package org.telegram.messenger;

import android.util.Base64;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * PrimeGram E2E: wire framing for the two kinds of tagged messages this feature sends -
 * an X3DH handshake/bundle message and a Double-Ratchet-encrypted chat message - as a
 * plain text-prefix convention, mirroring {@link PrimeBigFile#MARKER} rather than adding
 * any new {@code TLRPC.Message} field (this fork has none, and the established pattern
 * for "tag an ordinary message as protocol content" is a text/filename marker).
 *
 * <p>Pure encode/decode, no crypto and no I/O - {@link PrimeE2EManager} owns what the
 * bytes mean.
 */
final class PrimeE2EFraming {

    private static final String HANDSHAKE_PREFIX = "[PGE2E1:H ";
    private static final String CIPHERTEXT_PREFIX = "[PGE2E1:M ";
    /** No payload needed - which dialog it's about is implicit in which chat it arrived
     *  in, same as the handshake messages themselves. */
    private static final String CANCEL_MARKER = "[PGE2E1:C]";
    /** Sent as the *plaintext* of a normal ratchet-encrypted message (not a bare text
     *  marker like {@link #CANCEL_MARKER}) when an already-{@code ESTABLISHED} session
     *  is deliberately torn down - see {@code PrimeE2EManager.disableEncryption}'s doc
     *  for why this needs to be authenticated (encrypted under the real ratchet key)
     *  rather than a plain marker anyone able to write into the chat could forge. Uses
     *  control characters unlikely to collide with anything a real human would type, on
     *  the off chance decryption of a corrupted/adversarial ciphertext ever produces
     *  something resembling it by pure accident. */
    static final String DISABLE_SENTINEL = "PGE2E-DISABLE-v1";
    private static final String SUFFIX = "]";

    static final int SUBTYPE_REQUEST = 0;
    static final int SUBTYPE_RESPONSE = 1;

    private PrimeE2EFraming() {
    }

    static boolean isHandshake(String text) {
        return text != null && text.startsWith(HANDSHAKE_PREFIX) && text.endsWith(SUFFIX);
    }

    static boolean isCiphertext(String text) {
        return text != null && text.startsWith(CIPHERTEXT_PREFIX) && text.endsWith(SUFFIX);
    }

    static boolean isDisableSentinel(String text) {
        return DISABLE_SENTINEL.equals(text);
    }

    static boolean isCancel(String text) {
        return CANCEL_MARKER.equals(text);
    }

    static String encodeCancel() {
        return CANCEL_MARKER;
    }

    // ---- Handshake bundle ----

    static final class Bundle {
        int subtype;
        byte[] ikDhPub;
        byte[] ikSignPub;
        byte[] spkPub;
        byte[] spkSig;
        int spkId;
        final List<Integer> opkIndices = new ArrayList<>();
        final List<byte[]> opkPubs = new ArrayList<>();
        /** Only meaningful when {@code subtype == SUBTYPE_RESPONSE}: which of the
         *  request sender's one-time prekeys this reply consumed, so that side can
         *  mark it used and never reuse it. -1 = none available (degraded 3-DH). */
        int consumedOpkIndex = -1;
        /** Only meaningful when {@code subtype == SUBTYPE_RESPONSE}. Neither side has a
         *  live directory to fetch the other's prekey bundle from before the first
         *  message - so whoever *sends* the bundle-request is, cryptographically, the
         *  X3DH responder (their own IK/SPK/OPK bundle is what gets consumed), and
         *  whoever *replies* is the X3DH initiator: only the replier generates a fresh
         *  ephemeral (EK) and actually computes SK, carrying EK's public half back here
         *  so the original requester can mirror the same DH combination. See
         *  {@link PrimeE2EManager} for the full ceremony and why the UI-visible
         *  "who tapped Enable E2E" and the cryptographic initiator/responder roles are
         *  deliberately inverted from each other. */
        byte[] ephemeralPub;
    }

    static String encodeHandshake(Bundle bundle) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeByte(bundle.subtype);
            PrimeE2EIo.writeBytes(out, bundle.ikDhPub);
            PrimeE2EIo.writeBytes(out, bundle.ikSignPub);
            PrimeE2EIo.writeBytes(out, bundle.spkPub);
            PrimeE2EIo.writeBytes(out, bundle.spkSig);
            out.writeInt(bundle.spkId);
            out.writeInt(bundle.opkIndices.size());
            for (int i = 0; i < bundle.opkIndices.size(); i++) {
                out.writeInt(bundle.opkIndices.get(i));
                PrimeE2EIo.writeBytes(out, bundle.opkPubs.get(i));
            }
            out.writeInt(bundle.consumedOpkIndex);
            PrimeE2EIo.writeBytes(out, bundle.ephemeralPub);
            return HANDSHAKE_PREFIX + Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP) + SUFFIX;
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    /** @return the decoded bundle, or null on any malformed/truncated/hostile input -
     *  never throws, since this runs on every incoming message that merely starts with
     *  the marker prefix. */
    static Bundle decodeHandshake(String text) {
        if (!isHandshake(text)) {
            return null;
        }
        try {
            byte[] raw = Base64.decode(text.substring(HANDSHAKE_PREFIX.length(), text.length() - SUFFIX.length()), Base64.NO_WRAP);
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(raw));
            Bundle bundle = new Bundle();
            bundle.subtype = in.readByte();
            bundle.ikDhPub = requireLen(PrimeE2EIo.readBytes(in), PrimeE2ECrypto.KEY_LEN);
            bundle.ikSignPub = requireLen(PrimeE2EIo.readBytes(in), PrimeE2ECrypto.KEY_LEN);
            bundle.spkPub = requireLen(PrimeE2EIo.readBytes(in), PrimeE2ECrypto.KEY_LEN);
            bundle.spkSig = requireLen(PrimeE2EIo.readBytes(in), PrimeE2ECrypto.SIG_LEN);
            bundle.spkId = in.readInt();
            int opkCount = in.readInt();
            if (opkCount < 0 || opkCount > 200) {
                return null;
            }
            for (int i = 0; i < opkCount; i++) {
                bundle.opkIndices.add(in.readInt());
                bundle.opkPubs.add(requireLen(PrimeE2EIo.readBytes(in), PrimeE2ECrypto.KEY_LEN));
            }
            bundle.consumedOpkIndex = in.readInt();
            byte[] ephemeralPub = PrimeE2EIo.readBytes(in);
            bundle.ephemeralPub = (ephemeralPub != null && ephemeralPub.length == PrimeE2ECrypto.KEY_LEN) ? ephemeralPub : null;
            return bundle;
        } catch (Exception e) {
            return null;
        }
    }

    // ---- Ciphertext (Double Ratchet header + AES-GCM payload) ----

    static String encodeCiphertext(PrimeE2ERatchet.Encrypted msg) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            PrimeE2EIo.writeBytes(out, msg.header.ratchetPub);
            out.writeInt(msg.header.previousChainLength);
            out.writeInt(msg.header.messageNumber);
            PrimeE2EIo.writeBytes(out, msg.nonce);
            out.write(msg.ciphertext);
            return CIPHERTEXT_PREFIX + Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP) + SUFFIX;
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    static final class DecodedCiphertext {
        PrimeE2ERatchet.Header header;
        byte[] nonce;
        byte[] ciphertext;
    }

    static DecodedCiphertext decodeCiphertext(String text) {
        if (!isCiphertext(text)) {
            return null;
        }
        try {
            byte[] raw = Base64.decode(text.substring(CIPHERTEXT_PREFIX.length(), text.length() - SUFFIX.length()), Base64.NO_WRAP);
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(raw));
            byte[] ratchetPub = requireLen(PrimeE2EIo.readBytes(in), PrimeE2ECrypto.KEY_LEN);
            int previousChainLength = in.readInt();
            int messageNumber = in.readInt();
            byte[] nonce = requireLen(PrimeE2EIo.readBytes(in), PrimeE2ECrypto.GCM_NONCE_LEN);
            byte[] ciphertext = new byte[in.available()];
            in.readFully(ciphertext);

            DecodedCiphertext decoded = new DecodedCiphertext();
            decoded.header = new PrimeE2ERatchet.Header(ratchetPub, previousChainLength, messageNumber);
            decoded.nonce = nonce;
            decoded.ciphertext = ciphertext;
            return decoded;
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] requireLen(byte[] b, int len) {
        if (b == null || b.length != len) {
            throw new IllegalArgumentException("PrimeE2E: expected " + len + " bytes, got " + (b == null ? -1 : b.length));
        }
        return b;
    }
}
