package LZMA;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

public class LzmaInputStream extends FilterInputStream {
	boolean isClosed;
	CRangeDecoder RangeDecoder;
	byte[] dictionary;
	int dictionarySize;
	int dictionaryPos;
	int GlobalPos;
	int rep0;
	int rep1;
	int rep2;
	int rep3;
	int lc;
	int lp;
	int pb;
	int State;
	boolean PreviousIsMatch;
	int RemainLen;
	int[] probs;
	byte[] uncompressed_buffer;
	int uncompressed_size;
	int uncompressed_offset;
	long GlobalNowPos;
	long GlobalOutSize;
	static final int LZMA_BASE_SIZE = 1846;
	static final int LZMA_LIT_SIZE = 768;
	static final int kBlockSize = 65536;
	static final int kNumStates = 12;
	static final int kStartPosModelIndex = 4;
	static final int kEndPosModelIndex = 14;
	static final int kNumFullDistances = 128;
	static final int kNumPosSlotBits = 6;
	static final int kNumLenToPosStates = 4;
	static final int kNumAlignBits = 4;
	static final int kAlignTableSize = 16;
	static final int kMatchMinLen = 2;
	static final int IsMatch = 0;
	static final int IsRep = 192;
	static final int IsRepG0 = 204;
	static final int IsRepG1 = 216;
	static final int IsRepG2 = 228;
	static final int IsRep0Long = 240;
	static final int PosSlot = 432;
	static final int SpecPos = 688;
	static final int Align = 802;
	static final int LenCoder = 818;
	static final int RepLenCoder = 1332;
	static final int Literal = 1846;
	
	public LzmaInputStream(final InputStream inputStream) throws IOException {
		super(inputStream);
		isClosed = false;
		readHeader();
		fill_buffer();
	}
	
	private void LzmaDecode(final int n) throws IOException {
		final int n2 = (1 << pb) - 1;
		final int n3 = (1 << lp) - 1;
		uncompressed_size = 0;
		if (RemainLen == -1) {
			return;
		}
		while (RemainLen > 0 && uncompressed_size < n) {
			int n4 = dictionaryPos - rep0;
			if (n4 < 0) {
				n4 += dictionarySize;
			}
			uncompressed_buffer[uncompressed_size++] = (dictionary[dictionaryPos] = dictionary[n4]);
			if (++dictionaryPos == dictionarySize) {
				dictionaryPos = 0;
			}
			--RemainLen;
		}
		byte b;
		if (dictionaryPos == 0) {
			b = dictionary[dictionarySize - 1];
		} else {
			b = dictionary[dictionaryPos - 1];
		}
		while (uncompressed_size < n) {
			final int n5 = uncompressed_size + GlobalPos & n2;
			if (RangeDecoder.BitDecode(probs, 0 + (State << 4) + n5) == 0) {
				final int n6 = 1846 + 768 * (((uncompressed_size + GlobalPos & n3) << lc) + ((b & 0xFF) >> 8 - lc));
				if (State < 4) {
					State = 0;
				} else if (State < 10) {
					State -= 3;
				} else {
					State -= 6;
				}
				if (PreviousIsMatch) {
					int n7 = dictionaryPos - rep0;
					if (n7 < 0) {
						n7 += dictionarySize;
					}
					b = RangeDecoder.LzmaLiteralDecodeMatch(probs, n6, dictionary[n7]);
					PreviousIsMatch = false;
				} else {
					b = RangeDecoder.LzmaLiteralDecode(probs, n6);
				}
				uncompressed_buffer[uncompressed_size++] = b;
				dictionary[dictionaryPos] = b;
				if (++dictionaryPos != dictionarySize) {
					continue;
				}
				dictionaryPos = 0;
			} else {
				PreviousIsMatch = true;
				if (RangeDecoder.BitDecode(probs, 192 + State) == 1) {
					if (RangeDecoder.BitDecode(probs, 204 + State) == 0) {
						if (RangeDecoder.BitDecode(probs, 240 + (State << 4) + n5) == 0) {
							if (uncompressed_size + GlobalPos == 0) {
								throw new LzmaException("LZMA : Data Error");
							}
							State = ((State < 7) ? 9 : 11);
							int n8 = dictionaryPos - rep0;
							if (n8 < 0) {
								n8 += dictionarySize;
							}
							b = dictionary[n8];
							dictionary[dictionaryPos] = b;
							if (++dictionaryPos == dictionarySize) {
								dictionaryPos = 0;
							}
							uncompressed_buffer[uncompressed_size++] = b;
							continue;
						}
					} else {
						int rep0;
						if (RangeDecoder.BitDecode(probs, 216 + State) == 0) {
							rep0 = rep1;
						} else {
							if (RangeDecoder.BitDecode(probs, 228 + State) == 0) {
								rep0 = rep2;
							} else {
								rep0 = rep3;
								rep3 = rep2;
							}
							rep2 = rep1;
						}
						rep1 = this.rep0;
						this.rep0 = rep0;
					}
					RemainLen = RangeDecoder.LzmaLenDecode(probs, 1332, n5);
					State = ((State < 7) ? 8 : 11);
				} else {
					rep3 = rep2;
					rep2 = rep1;
					rep1 = rep0;
					State = ((State < 7) ? 7 : 10);
					RemainLen = RangeDecoder.LzmaLenDecode(probs, 818, n5);
					final int bitTreeDecode = RangeDecoder.BitTreeDecode(probs, 432 + (((RemainLen < 4) ? RemainLen : 3) << 6), 6);
					if (bitTreeDecode >= 4) {
						final int n9 = (bitTreeDecode >> 1) - 1;
						rep0 = (0x2 | (bitTreeDecode & 0x1)) << n9;
						if (bitTreeDecode < 14) {
							rep0 += RangeDecoder.ReverseBitTreeDecode(probs, 688 + rep0 - bitTreeDecode - 1, n9);
						} else {
							rep0 += RangeDecoder.DecodeDirectBits(n9 - 4) << 4;
							rep0 += RangeDecoder.ReverseBitTreeDecode(probs, 802, 4);
						}
					} else {
						rep0 = bitTreeDecode;
					}
					++rep0;
				}
				if (rep0 == 0) {
					RemainLen = -1;
					break;
				}
				if (rep0 > uncompressed_size + GlobalPos) {
					throw new LzmaException("LZMA : Data Error");
				}
				RemainLen += 2;
				do {
					int n10 = dictionaryPos - rep0;
					if (n10 < 0) {
						n10 += dictionarySize;
					}
					b = dictionary[n10];
					dictionary[dictionaryPos] = b;
					if (++dictionaryPos == dictionarySize) {
						dictionaryPos = 0;
					}
					uncompressed_buffer[uncompressed_size++] = b;
					--RemainLen;
				} while (RemainLen > 0 && uncompressed_size < n);
			}
		}
		GlobalPos += uncompressed_size;
	}
	
	private void fill_buffer() throws IOException {
		if (GlobalNowPos < GlobalOutSize) {
			uncompressed_offset = 0;
			final long n = GlobalOutSize - GlobalNowPos;
			int n2;
			if (n > 65536L) {
				n2 = 65536;
			} else {
				n2 = (int) n;
			}
			LzmaDecode(n2);
			if (uncompressed_size == 0) {
				GlobalOutSize = GlobalNowPos;
			} else {
				GlobalNowPos += uncompressed_size;
			}
		}
	}
	
	private void readHeader() throws IOException {
		final byte[] array = new byte[5];
		if (5 != in.read(array)) {
			throw new LzmaException("LZMA header corrupted : Properties error");
		}
		GlobalOutSize = 0L;
		for (int i = 0; i < 8; ++i) {
			final int read = in.read();
			if (read == -1) {
				throw new LzmaException("LZMA header corrupted : Size error");
			}
			GlobalOutSize += read << i * 8;
		}
		if (GlobalOutSize == -1L) {
			GlobalOutSize = Long.MAX_VALUE;
		}
		int j = array[0] & 0xFF;
		if (j >= 225) {
			throw new LzmaException("LZMA header corrupted : Properties error");
		}
		pb = 0;
		while (j >= 45) {
			++pb;
			j -= 45;
		}
		lp = 0;
		while (j >= 9) {
			++lp;
			j -= 9;
		}
		lc = j;
		probs = new int[1846 + (768 << lc + lp)];
		dictionarySize = 0;
		for (int k = 0; k < 4; ++k) {
			dictionarySize += (array[1 + k] & 0xFF) << k * 8;
		}
		dictionary = new byte[dictionarySize];
		if (dictionary == null) {
			throw new LzmaException("LZMA : can't allocate");
		}
		final int n = 1846 + (768 << lc + lp);
		RangeDecoder = new CRangeDecoder(in);
		dictionaryPos = 0;
		GlobalPos = 0;
		final boolean b = true;
		rep3 = (b ? 1 : 0);
		rep2 = (b ? 1 : 0);
		rep1 = (b ? 1 : 0);
		rep0 = (b ? 1 : 0);
		State = 0;
		PreviousIsMatch = false;
		RemainLen = 0;
		dictionary[dictionarySize - 1] = 0;
		for (int l = 0; l < n; ++l) {
			probs[l] = 1024;
		}
		uncompressed_buffer = new byte[65536];
		uncompressed_size = 0;
		uncompressed_offset = 0;
		GlobalNowPos = 0L;
	}
	
	@Override
	public int read(final byte[] array, final int n, final int n2) throws IOException {
		if (isClosed) {
			throw new IOException("stream closed");
		}
		if ((n | n2 | n + n2 | array.length - (n + n2)) < 0) {
			throw new IndexOutOfBoundsException();
		}
		if (n2 == 0) {
			return 0;
		}
		if (uncompressed_offset == uncompressed_size) {
			fill_buffer();
		}
		if (uncompressed_offset == uncompressed_size) {
			return -1;
		}
		final int min = Math.min(n2, uncompressed_size - uncompressed_offset);
		System.arraycopy(uncompressed_buffer, uncompressed_offset, array, n, min);
		uncompressed_offset += min;
		return min;
	}
	
	@Override
	public void close() throws IOException {
		isClosed = true;
		super.close();
	}
}
