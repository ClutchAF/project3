package nachos.vm;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;
import java.util.*;

/**
 * A <tt>UserProcess</tt> that supports demand-paging.
 */
public class VMProcess extends UserProcess {
	/**
	 * Allocate a new process.
	 */
	public VMProcess() {
		super();
	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
		super.saveState();
		
		Processor processor = Machine.processor();	
		int tlbSize = processor.getTLBSize();

		for(int i = 0; i < tlbSize; i++){
			TranslationEntry entry = processor.readTLBEntry(i);
			if(entry.valid){
				pageTable[entry.vpn] = entry;
				entry.valid = false;
				processor.writeTLBEntry(i, entry);
				entry.valid = true;
			}
		}
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
	}

	/**
	 * Initializes page tables for this process so that the executable can be
	 * demand-paged.
	 * 
	 * @return <tt>true</tt> if successful.
	 */
	protected boolean loadSections() {
		Processor processor = Machine.processor();

		if(processor.getNumPhysPages() < numPages){
			coff.close();
			Lib.debug(dbgProcess, "\tinsufficient physical memory");
			return false;
		}

		for(int s = 0; s < coff.getNumSections(); s++){
			CoffSection section = coff.getSection(s);

			Lib.debug(dbgProcess, "\tinitializing " + section.getName()
					+ " section (" + section.getLength() + " pages)");

			for (int i = 0; i < section.getLength(); i++) {
				int vpn = section.getFirstVPN() + i;
				
				TranslationEntry entry = pageTable[vpn];

				coffMap.put(vpn, section);

				entry.valid = false;
				entry.readOnly = section.isReadOnly();
			}
		}
		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		super.unloadSections();
	}

	/**
	 * Handle a user exception. Called by <tt>UserKernel.exceptionHandler()</tt>
	 * . The <i>cause</i> argument identifies which exception occurred; see the
	 * <tt>Processor.exceptionZZZ</tt> constants.
	 * 
	 * @param cause the user exception that occurred.
	 */
	public void handleException(int cause) {
		Processor processor = Machine.processor();

		switch (cause) {
		case Processor.exceptionTLBMiss: handleTLBMiss(processor.readRegister(Processor.regBadVAddr)); break;
		default:
			super.handleException(cause);
			break;
		}
	}

	public void handleTLBMiss(int vaddr){
		Processor processor = Machine.processor();
	
		/** STEP 1 */	
		// get virtual page number
		int vpn = processor.pageFromAddress(vaddr);
		
		TranslationEntry inputEntry = null;

		// Get size of TLB
		int tlbSize = processor.getTLBSize();

		// use vpn to locate pte in page table
		TranslationEntry pte = pageTable[vpn];

		// Index in TLB to evict
		int evictIndex = 0;

		/** STEP 2 */
		// Loop through TLB to find invalid entry
		for(int i = 0; i < tlbSize; i++){
			TranslationEntry entry = processor.readTLBEntry(i);
			if(!entry.valid){
			// need to evict page somehow. I think writeTLBEntry overwrites it?
				evictIndex = i;
				break;
			}
			
			// if all of the entries are valid, evict random one
			if(i == tlbSize - 1){
				evictIndex = Lib.random(tlbSize);
				entry = processor.readTLBEntry(evictIndex);
				
				// Synch victim's page table entry
				if(entry.valid){
					int victimVPN = entry.vpn;
					pageTable[victimVPN].dirty = entry.dirty;
					pageTable[victimVPN].used = entry.used;
				}
				break;
			}
		}
		
		/** STEP 3 */
		processor.writeTLBEntry(evictIndex, pte);
	     //	}
	}

	private HashMap<Integer, CoffSection> coffMap = new HashMap<Integer, CoffSection>();
	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';

	private static final char dbgVM = 'v';
}
