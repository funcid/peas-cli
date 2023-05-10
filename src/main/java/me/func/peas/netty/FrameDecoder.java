/*
 * Copyright (C) 2018-2023 Velocity Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.func.peas.netty;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

import static me.func.peas.netty.WellKnownExceptions.BAD_LENGTH_CACHED;
import static me.func.peas.netty.WellKnownExceptions.VARINT_BIG_CACHED;

/// https://github.com/PaperMC/Velocity

public class FrameDecoder extends ByteToMessageDecoder {
  private final VarintByteDecoder reader = new VarintByteDecoder();

  public FrameDecoder() {}

  public void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
    if (!ctx.channel().isActive()) {
      in.clear();
      return;
    }

    reader.reset();

    int varintEnd = in.forEachByte(reader);
    if (varintEnd == -1) {
      // We tried to go beyond the end of the buffer. This is probably a good sign that the
      // buffer was too short to hold a proper varint.
      if (reader.getResult() == VarintByteDecoder.DecodeResult.RUN_OF_ZEROES) {
        // Special case where the entire packet is just a run of zeroes. We ignore them all.
        in.clear();
      }
      return;
    }

    if (reader.getResult() == VarintByteDecoder.DecodeResult.RUN_OF_ZEROES) {
      // this will return to the point where the next varint starts
      in.readerIndex(varintEnd);
    } else if (reader.getResult() == VarintByteDecoder.DecodeResult.SUCCESS) {
      int readVarint = reader.getReadVarint();
      int bytesRead = reader.getBytesRead();
      if (readVarint < 0) {
        in.clear();
        throw BAD_LENGTH_CACHED;
      } else if (readVarint == 0) {
        // skip over the empty packet(s) and ignore it
        in.readerIndex(varintEnd + 1);
      } else {
        int minimumRead = bytesRead + readVarint;
        if (in.isReadable(minimumRead)) {
          out.add(in.retainedSlice(varintEnd + 1, readVarint));
          in.skipBytes(minimumRead);
        }
      }
    } else if (reader.getResult() == VarintByteDecoder.DecodeResult.TOO_BIG) {
      in.clear();
      throw VARINT_BIG_CACHED;
    }
  }
}
