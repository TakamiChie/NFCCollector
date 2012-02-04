package jp.android.takamichie.NFCCollector;

import java.nio.ByteBuffer;

public class Item {

    public Item(byte[] data) {
	this(byteArrayToLong(data));
    }

    public Item(long id) {
	this.id = id;
	StringBuffer strbuf = new StringBuffer(8 * 2);
	strbuf.append(String.format("%016X", id));
	idString = "0x" + strbuf.toString();
    }

    public String idToString() {
	return idString;
    }

    private String itemName;
    private long id;
    private String idString;
    private boolean has;

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public long getId() {
        return id;
    }

    public boolean isHas() {
        return has;
    }

    public void setHas(boolean has) {
        this.has = has;
    }


    private static long byteArrayToLong(byte[] data) {
	ByteBuffer buf = ByteBuffer.allocate(8);
	for (int i = data.length; i < buf.capacity(); i++)
	    buf.put((byte) 0);
	buf.put(data);
	buf.flip();
	return buf.getLong();
    }

}
