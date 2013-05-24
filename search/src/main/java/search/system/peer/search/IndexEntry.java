/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package search.system.peer.search;

import java.io.Serializable;
import java.util.UUID;

/**
 *
 * @author jdowling
 */
public class IndexEntry implements Serializable {

    private final int indexId;
    private final String text;
    private final UUID id;

    public IndexEntry(int indexId, String text) {
	this.indexId = indexId;
	this.text = text;
	id = UUID.randomUUID();
    }

    public int getIndexId() {
	return indexId;
    }

    public String getText() {
	return text;
    }
}
