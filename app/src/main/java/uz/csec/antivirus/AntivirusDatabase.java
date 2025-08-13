package uz.csec.antivirus;

import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteDatabase;
import android.content.Context;
public class AntivirusDatabase extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "database.db";
    private static final int DATABASE_VERSION = 1;

    public AntivirusDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String CREATE_TABLE = "CREATE TABLE virus_files (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "file_name TEXT, " +
                "file_path TEXT, " +
                "file_size INTEGER, " +
                "detected_at TEXT, " +
                "hash TEXT UNIQUE)";
        db.execSQL(CREATE_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS virus_files");
        onCreate(db);
    }

    public void insertVirusFile(String fileName, String filePath, long fileSize, String detectedAt, String hash) {
        SQLiteDatabase db = this.getWritableDatabase();
        android.content.ContentValues values = new android.content.ContentValues();
        values.put("file_name", fileName);
        values.put("file_path", filePath);
        values.put("file_size", fileSize);
        values.put("detected_at", detectedAt);
        values.put("hash", hash);
        db.insertWithOnConflict("virus_files", null, values, SQLiteDatabase.CONFLICT_IGNORE);
        db.close();
    }

    public int getVirusCount() {
        SQLiteDatabase db = this.getReadableDatabase();
        android.database.Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM virus_files", null);
        int count = 0;
        if (cursor.moveToFirst()) {
            count = cursor.getInt(0);
        }
        cursor.close();
        db.close();
        return count;
    }
}
