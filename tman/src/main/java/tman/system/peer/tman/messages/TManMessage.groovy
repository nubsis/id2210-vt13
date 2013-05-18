package tman.system.peer.tman.messages

import se.sics.kompics.address.Address
import se.sics.kompics.network.Message
import se.sics.kompics.network.Transport

public abstract class TManMessage extends Message {

	protected final UUID id

	public TManMessage(Address source, Address destination) {
		this(source, destination, Transport.TCP, true);
	}

	public TManMessage(Address source, Address destination, UUID id)
	{
		this(source, destination)
		this.id = id;
	}

	public TManMessage(Address source, Address destination, Transport protocol) {
		this(source, destination, protocol, true);
	}

	public TManMessage(Address source, Address destination, Transport protocol,
	boolean highPriority) {
		super(source, destination, protocol, highPriority);
		id = UUID.randomUUID();
	}

	public UUID getId()
	{
		return id;
	}

}
