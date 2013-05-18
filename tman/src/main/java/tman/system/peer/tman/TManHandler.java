package tman.system.peer.tman;

import java.util.UUID;

import se.sics.kompics.Handler;
import tman.system.peer.tman.messages.TManMessage;

public abstract class TManHandler<E extends TManMessage> extends Handler<E> {
	protected final TMan tman;
	protected final UUID id;

	public TManHandler(TMan tman)
	{
		this.tman = tman;
		this.id = UUID.randomUUID();
	}

	public UUID getId() {
		return id;
	}
}
