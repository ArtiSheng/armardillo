package armadillo.utils.axml.EditXml.decode;

import armadillo.utils.axml.EditXml.io.ZInput;
import armadillo.utils.axml.EditXml.io.ZOutput;
import armadillo.utils.axml.Manifest_ids;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class StringBlock implements IAXMLSerialize {
    private static final int TAG = 0x001C0001;
    private static final int INT_SIZE = 4;
    private ResBlock mResBlock;
    private int mChunkSize;
    private int mStringsCount;
    private int mStylesCount;
    private int mEncoder;

    private int mStrBlockOffset;
    private int mStyBlockOffset;

    private int[] mPerStrOffset;
    private int[] mPerStyOffset;

    public void setmResBlock(ResBlock mResBlock) {
        this.mResBlock = mResBlock;
    }

    public List<String> getmStrings() {
        return mStrings;
    }

    private List<String> mStrings;

    private List<Style> mStyles;

    public int getStringMapping(String str) {
        int size = mStrings.size();
        for (int i = 0; i < size; i++) {
            if (mStrings.get(i).equals(str)) {
                return i;
            }
        }
        return -1;
    }

    public int putString(String str) {
        if (containsString(str)) {
            return getStringMapping(str);
        }
        return addString(str);
    }

    public int addString(String str) {
        int id = Manifest_ids.getInstance().parseids(str);
        if (id != -1) {
            mStrings.add(mResBlock.getResourceIds().length, str);
            return getStringMapping(str);
        }
        mStrings.add(str);
        return (mStrings.size() - 1);
    }

    public String setString(int index, String str) {
        return mStrings.set(index, str);
    }

    public String removeString(int index) {
        return mStrings.remove(index);
    }

    public boolean containsString(String str) {
        return mStrings.contains(str.trim());
    }

    public int getStringCount() {
        return mStrings.size();
    }

    private static int[] getVarint(byte[] array, int offset) {
        if ((array[offset] & 0x80) == 0)
            return new int[]{array[offset] & 0x7f, 1};
        else {
            return new int[]{
                    ((array[offset] & 0x7f) << 8) | array[offset + 1] & 0xFF, 2};
        }
    }


    /**
     * Reads whole (including chunk type) string block from stream.
     * Stream must be at the chunk type.
     */
    public void read(ZInput reader) throws IOException {
        mChunkSize = reader.readInt();
        mStringsCount = reader.readInt();
        mStylesCount = reader.readInt();
        mEncoder = reader.readInt();//utf-8 or uft16
        mStrBlockOffset = reader.readInt();
        mStyBlockOffset = reader.readInt();
        if (mStringsCount > 0) {
            mPerStrOffset = reader.readIntArray(mStringsCount);
            mStrings = new ArrayList<>(mStringsCount);
        }
        if (mStylesCount > 0) {
            mPerStyOffset = reader.readIntArray(mStylesCount);
            mStyles = new ArrayList<Style>();
        }
        if (mStringsCount > 0) {
            int size = ((mStyBlockOffset == 0) ? mChunkSize : mStyBlockOffset) - mStrBlockOffset;
            byte[] rawStrings = new byte[size];
            reader.readFully(rawStrings);
            for (int i = 0; i < mStringsCount; i++) {
                if ((mEncoder & 0x100) != 0) {
                    int offset = mPerStrOffset[i];
                    offset += getVarint(rawStrings, mPerStrOffset[i])[1];
                    int[] varint = getVarint(rawStrings, offset);
                    offset += varint[1];
                    int length = varint[0];
                    mStrings.add(i, new String(rawStrings, offset, length, StandardCharsets.UTF_8));
                } else {
                    int offset = mPerStrOffset[i];
                    //short len = toShort(rawStrings[offset], rawStrings[offset + 1]);
                    short len = getShort(rawStrings,offset);
                    mStrings.add(i, new String(rawStrings, offset + 2, len * 2, StandardCharsets.UTF_16LE));
                }
            }
        }
        if (mStylesCount > 0) {
            int size = mChunkSize - mStyBlockOffset;
            int[] styles = reader.readIntArray(size / 4);


            for (int i = 0; i < mStylesCount; i++) {
                int offset = mPerStyOffset[i];
                int j = offset;
                for (; j < styles.length; j++) {
                    if (styles[j] == -1) break;
                }

                int[] array = new int[j - offset];
                System.arraycopy(styles, offset, array, 0, array.length);
                Style d = Style.parse(array);

                mStyles.add(d);
            }
        }
    }

    @Override
    public void write(ZOutput writer) throws IOException {
        int size = 0;
        size += writer.writeInt(TAG);
        size += writer.writeInt(mChunkSize);
        size += writer.writeInt(mStringsCount);
        size += writer.writeInt(mStylesCount);
        size += writer.writeInt(0);
        size += writer.writeInt(mStrBlockOffset);
        size += writer.writeInt(mStyBlockOffset);
        if (mPerStrOffset != null)
            for (int offset : mPerStrOffset)
                size += writer.writeInt(offset);
        if (mPerStyOffset != null)
            for (int offset : mPerStyOffset)
                size += writer.writeInt(offset);
        if (mStrings != null)
            for (String s : mStrings) {
                byte[] raw = s.getBytes("UTF-16LE");
                size += writer.writeShort((short) (s.length()));
                size += writer.writeFully(raw, 0, raw.length);
                size += writer.writeShort((short) 0);
            }
        if (mStyles != null)
            for (Style style : mStyles)
                style.write(writer);
        if (mChunkSize > size)
            writer.writeShort((short) 0);
    }

    protected static byte[] getVarBytes(int val) {
        if ((val & 0x7f) == val)// 111 1111
            return new byte[]{(byte) val};
        else {
            byte[] b = new byte[2];
            b[0] = (byte) (val >>> 8 | 0x80);
            b[1] = (byte) (val & 0xff);
            return b;
        }
    }

    public void prepare() throws IOException {
        //mStrings
        mStringsCount = mStrings == null ? 0 : mStrings.size();
        mStylesCount = mStyles == null ? 0 : mStyles.size();

        //string & style block offset
        int base = INT_SIZE * 7;//from 0 to string array

        int strSize = 0;
        int[] perStrSize = null;

        if (mStrings != null) {
            int size = 0;
            perStrSize = new int[mStrings.size()];
            for (int i = 0; i < mStrings.size(); i++) {
                perStrSize[i] = size;
                try {
                    size += 2 + mStrings.get(i).getBytes("UTF-16LE").length + 2;
                } catch (UnsupportedEncodingException e) {
                    throw new IOException(e);
                }
            }
            strSize = size;
        }

        int stySize = 0;
        int[] perStySize = null;
        if (mStyles != null) {
            int size = 0;
            perStySize = new int[mStyles.size()];
            for (int i = 0; i < mStyles.size(); i++) {
                perStySize[i] = size;
                size += mStyles.get(i).getSize();
            }
            stySize = size;
        }

        int string_array_size = perStrSize == null ? 0 : perStrSize.length * INT_SIZE;
        int style_array_size = perStySize == null ? 0 : perStySize.length * INT_SIZE;

        if (mStrings != null && mStrings.size() > 0) {
            mStrBlockOffset = base + string_array_size + style_array_size;
            mPerStrOffset = perStrSize;
        } else {
            mStrBlockOffset = 0;
            mPerStrOffset = null;
        }

        if (mStyles != null && mStyles.size() > 0) {
            mStyBlockOffset = base + string_array_size + style_array_size + strSize;
            mPerStyOffset = perStySize;
        } else {
            mStyBlockOffset = 0;
            mPerStyOffset = null;
        }

        mChunkSize = base + string_array_size + style_array_size + strSize + stySize;

        int align = mChunkSize % 4;
        if (align != 0) {
            mChunkSize += (INT_SIZE - align);
        }
    }

    public int getSize() {
        return mChunkSize;
    }

    public String getStringFor(int index) {
        if (index < 0)
            return null;
        if (index >= mStrings.size())
            return null;
        return mStrings.get(index);
    }

    private short toShort(short byte1, short byte2) {
        return (short) ((byte2 << 8) + byte1);
    }
    private static short getShort(byte[] array, int offset) {
        return (short) ((array[offset + 1] & 0xFF) << 8 | array[offset] & 0xFF);
    }

    public Style getStyle(int index) {
        return mStyles.get(index);
    }

    public StringBlock() {
    }

    @Override
    public int getType() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public void setSize(int size) {
        // TODO Auto-generated method stub

    }

    @Override
    public void setType(int type) {
        // TODO Auto-generated method stub

    }

    public static class Style {
        List<Decorator> mDct;

        public Style() {
            mDct = new ArrayList<Decorator>();
        }

        public List<Decorator> getDecorator() {
            return mDct;
        }

        public void addStyle(Decorator style) {
            mDct.add(style);
        }

        public int getSize() {
            int size = 0;
            size += getCount() * Decorator.SIZE;
            size += 1;//[-1] as a seperator
            return size;
        }

        public int getCount() {
            return mDct.size();
        }

        public static Style parse(int[] muti_triplet) throws IOException {
            if (muti_triplet == null || (muti_triplet.length % Decorator.SIZE != 0)) {
                throw new IOException("Fail to parse style");
            }

            Style d = new Style();

            Decorator style = null;
            for (int i = 0; i < muti_triplet.length; i++) {
                if (i % Decorator.SIZE == 0) {
                    new Decorator();
                }

                switch (i % 3) {
                    case 0: {
                        style = new Decorator();
                        style.mTag = muti_triplet[i];
                    }
                    break;
                    case 1: {
                        style.mDoctBegin = muti_triplet[i];
                    }
                    break;
                    case 2: {
                        style.mDoctEnd = muti_triplet[i];
                        d.mDct.add(style);
                    }
                    break;
                }
            }

            return d;
        }

        public int write(ZOutput writer) throws IOException {
            int size = 0;
            if (mDct != null && mDct.size() > 0) {
                for (Decorator dct : mDct) {
                    size += writer.writeInt(dct.mTag);
                    size += writer.writeInt(dct.mDoctBegin);
                    size += writer.writeInt(dct.mDoctEnd);
                }
                size += writer.writeInt(-1);
            }
            return size;
        }
    }

    public static class Decorator {
        public static final int SIZE = 3;

        public int mTag;
        public int mDoctBegin;
        public int mDoctEnd;

        public Decorator(int[] triplet) {
            mTag = triplet[0];
            mDoctBegin = triplet[1];
            mDoctEnd = triplet[2];
        }

        public Decorator() {
        }

    }

    @Override
    public String toString() {
        return "StringBlock{" +
                "mChunkSize=" + mChunkSize +
                ", mStringsCount=" + mStringsCount +
                ", mStylesCount=" + mStylesCount +
                ", mEncoder=" + mEncoder +
                ", mStrBlockOffset=" + mStrBlockOffset +
                ", mStyBlockOffset=" + mStyBlockOffset +
                ", mPerStrOffset=" + Arrays.toString(mPerStrOffset) +
                ", mPerStyOffset=" + Arrays.toString(mPerStyOffset) +
                ", mStrings=" + mStrings +
                ", mStyles=" + mStyles +
                '}';
    }
}
