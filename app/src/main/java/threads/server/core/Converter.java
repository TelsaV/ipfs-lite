package threads.server.core;

import androidx.room.TypeConverter;

import threads.server.ipfs.CID;


public class Converter {


    @TypeConverter
    public static CID toCID(String cid) {
        return cid == null ? null : CID.create(cid);
    }

    @TypeConverter
    public static String toString(CID cid) {
        if (cid == null) {
            return null;
        } else {
            return cid.getCid();
        }
    }
}
