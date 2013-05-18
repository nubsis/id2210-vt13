package tman.system.peer.tman.messages;

import se.sics.kompics.address.Address;
import se.sics.kompics.network.Message;

public class ElectionRequest extends Message {

    /**
     * The peer that made this request
     */
    private final Address elector;

    public ElectionRequest(Address src, Address dst) {
        super(src, dst);
        elector = src;
    }

    public Address getElector() {
        return elector;
    }
}
