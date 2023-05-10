package me.func.peas.netty;

/// https://github.com/astei/krypton

public final class WellKnownExceptions {
  private WellKnownExceptions() {}

  public static final QuietDecoderException BAD_LENGTH_CACHED = new QuietDecoderException("Bad packet length");
  public static final QuietDecoderException BAD_VARINT_CACHED = new QuietDecoderException("Bad VarInt decoded");
  public static final QuietDecoderException VARINT_BIG_CACHED = new QuietDecoderException("VarInt too big");
}
