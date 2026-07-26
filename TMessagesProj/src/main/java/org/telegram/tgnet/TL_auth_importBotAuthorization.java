package org.telegram.tgnet;

/**
 * PrimeGram: {@code auth.importBotAuthorization}, the MTProto call that trades a BotFather
 * token for a real authorization.
 *
 * <p>Kept in its own file rather than added to {@link TLRPC}: that file is generated from the
 * schema and gets rewritten wholesale on every upstream merge, which would silently drop this.
 */
public class TL_auth_importBotAuthorization extends TLObject {

    public static final int constructor = 0x67a3ff2c;

    /** Reserved by the schema; the server requires it to be zero. */
    public int flags;
    public int api_id;
    public String api_hash;
    public String bot_auth_token;

    @Override
    public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
        return TLRPC.auth_Authorization.TLdeserialize(stream, constructor, exception);
    }

    @Override
    public void serializeToStream(OutputSerializedData stream) {
        stream.writeInt32(constructor);
        stream.writeInt32(flags);
        stream.writeInt32(api_id);
        stream.writeString(api_hash);
        stream.writeString(bot_auth_token);
    }
}
