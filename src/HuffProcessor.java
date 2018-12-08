import java.util.PriorityQueue;

/**
 * Although this class has a history of several years,
 * it is starting from a blank-slate, new and clean implementation
 * as of Fall 2018.
 * <P>
 * Changes include relying solely on a tree for header information
 * and including debug and bits read/written information
 * 
 * @author Owen Astrachan
 */

public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD); 
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE  = HUFF_NUMBER | 1;

	private final int myDebugLevel;

	public static final int DEBUG_HIGH = 4;
	public static final int DEBUG_LOW = 1;

	public HuffProcessor() {
		this(0);
	}

	public HuffProcessor(int debug) {
		myDebugLevel = debug;
	}

	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be compressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream in, BitOutputStream out){

		int[] counts = readForCounts(in, out);  // added out for out. methods
		HuffNode root = makeTreeFromCounts(counts);
		String[] codings = makeCodingsFromTree(root);

		out.writeBits(BITS_PER_INT, HUFF_TREE);
		writeHeader(root,out);

		in.reset();
		writeCompressedBits(codings,in,out);
		out.close();
	}

	private int[] readForCounts(BitInputStream in, BitOutputStream out) {
		// WRITE HELPER METHOD TO DETERMINE FREQS PG 10
		int[] store257 = new int[ALPH_SIZE + 1];
		store257[PSEUDO_EOF] = 1;
		while (true){
			int val = in.readBits(BITS_PER_WORD);
			if (val == -1) break;
			out.writeBits(BITS_PER_WORD, val);
		}
		out.close();
		return store257;
	}

	private HuffNode makeTreeFromCounts(int[] counts) {
		// WRITE HELPER METHOD TO MAKE CODINGS FROM TRIE/TREE
		PriorityQueue<HuffNode> pq = new PriorityQueue<HuffNode>();

		for (int a=0; a<counts.length; a++) {
			if (counts[a]>0) {
				pq.add(new HuffNode(a, counts[a], null, null));
			}
		}
		while (pq.size() > 1) {
			HuffNode left = pq.remove();
			HuffNode right = pq.remove();
			HuffNode t = new HuffNode(0, left.myWeight+right.myWeight, left, right);
			pq.add(t);
		}
		HuffNode root = pq.remove();
		return root;
	}

	private String[] makeCodingsFromTree(HuffNode root) {
		// WRITE HELPER METHOD TO MAKE CODINGS FROM TRIE/TREE
		String[] encodings = new String[ALPH_SIZE + 1];
		codingHelper(root, "", encodings);	// ANOTHER HELPER METHOD
		return encodings;	
	}

	private void codingHelper(HuffNode root, String path, String[] encodings) {
		if (root.myValue == 1) {
			encodings[root.myValue] = path;
			return;
		}
		else {
			codingHelper(root.myLeft, path+"0", encodings);
			codingHelper(root.myRight, path+"1", encodings);
		}
	}

	private void writeHeader(HuffNode root, BitOutputStream out) {
		if (root.myValue == 0) {
			out.writeBits(1, 0);
			writeHeader(root.myLeft,out);
			writeHeader(root.myRight,out);
		} else {
			String holdMe = "1" + String.valueOf(root.myValue);
			int holdStr = Integer.valueOf(holdMe);
			out.writeBits(1+BITS_PER_WORD+1, holdStr);
		}
	}

	private void writeCompressedBits(String[] codings, BitInputStream in, BitOutputStream out) {
		// WRITE HELPER METHOD
		for (int a=0; a<codings.length; a++) {
			String code = codings[a];
			out.writeBits(code.length(), Integer.parseInt(code));
		}
		String code = codings[PSEUDO_EOF];
		out.writeBits(code.length(), Integer.parseInt(code));
	}



	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be decompressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void decompress(BitInputStream in, BitOutputStream out){

		//		Just printing the contents in the input file to the output file:
		//		while (true){
		//			int val = in.readBits(BITS_PER_WORD);
		//			if (val == -1) break;
		//			out.writeBits(BITS_PER_WORD, val);
		//		}
		//		out.close();

		int bits = in.readBits(BITS_PER_INT);								// Helper from BitInputStream
		if (bits != HUFF_TREE) {
			throw new HuffException("illegal header starts with "+bits);	// From HuffException
		}
		if (bits == -1) {
			throw new HuffException("reading bits failed");					// Failed if return -1
		}
		HuffNode root = readTreeHeader(in);									// Write this helper method
		readCompressedBits(root,in,out);									// Write this helper method // ERROR?
		out.close();
	}

	private HuffNode readTreeHeader(BitInputStream in) {
		// WRITE HELPER HERE PG 8
		int bitRead = in.readBits(1);										// Read a single bit
		if (bitRead == -1) {												// If the bit is -1
			throw new HuffException("bad input");							// Throw exception
		}
		if (bitRead ==  0) {												// If it's an inner node
			HuffNode leftHN = readTreeHeader(in);							// Recursive call
			HuffNode rightHN = readTreeHeader(in);							// Recursive call
			return new HuffNode(0,0,leftHN,rightHN);						// Return new HN

		} else {															// If 1 (leaf)
			int value = in.readBits(BITS_PER_WORD + 1);						// Get value
			return new HuffNode(value, 0, null, null);						// Return new HN
		}
	}

	private void readCompressedBits(HuffNode root, BitInputStream in, BitOutputStream out) {
		// WRITE HELPER HERE PG 9
		HuffNode current = root;
		while (true) {
			int bits = in.readBits(1);
			if (bits == -1) {
				throw new HuffException("bad input, no PSEUDO_EOF");
			} 
			else {
				if (bits == 0) {current = current.myLeft;} 
				else {current = current.myRight;}
				
				if (bits == 1) { // ERROR?  Deleted:  || current.myValue == PSEUDO_EOF
					if (current.myValue == PSEUDO_EOF) break; 
					else {
						out.writeBits(BITS_PER_WORD, current.myValue);
						current = root;
					}
				}
			}
		}
	}
}








