package tman.system.peer.tman.election;

/** The current election status as seen by this node */
public enum ElectionStatus {
	/** A leader has been elected */
	KNOWN,
	/** There is currently an election is progress */
	ELECTION, 
	/** The leader is currently unknown */
	UNKNOWN
}
