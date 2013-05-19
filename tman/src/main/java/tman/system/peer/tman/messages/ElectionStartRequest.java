package tman.system.peer.tman.messages;

import se.sics.kompics.address.Address;
import se.sics.kompics.network.Message;

public class ElectionStartRequest extends Message {

    /**
     * The peer that made this request
     */
    private final Address elector;

    public ElectionStartRequest(Address src, Address dst) {
        super(src, dst);
        elector = src;
    }

    public Address getElector() {
        return elector;
    }
}
