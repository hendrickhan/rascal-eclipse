package org.rascalmpl.eclipse.console.internal;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class ConsoleSyncer implements IBufferFlushNotifier {
	
	public class SyncThread extends Thread {
		private final ConcurrentCircularOutputStream source;
		private final Semaphore flushStream;
		private final PausableOutputStream target;

		public SyncThread(ConcurrentCircularOutputStream source, PausableOutputStream target, Semaphore flushStream) {
			super("Console Sync Thread");
			this.source = source;
			this.target = target;
			this.flushStream = flushStream;
		}

		@Override
		public void run() {
			try {
				while (true) {
					// either sleep for 50ms or get a signal to empty the stream earlier
					flushStream.tryAcquire(50L, TimeUnit.MILLISECONDS);
					if (!target.isPaused()) {
						byte[] bufferContents = source.getBufferCopy();
						flushStream.drainPermits(); // reset semaphore
						if (bufferContents.length > 0) {
							try {
								target.write(bufferContents);
								target.flush();
							} catch (IOException e) {
								System.err.println("Couldn't send stuff to the actuall console");
							}
						}
					} 
					else {
						flushStream.drainPermits(); // reset semaphore
					}
				}
			} catch (InterruptedException e) {
				return;
			}
		}
	}

	private PausableOutputStream target;
	private Semaphore flushStream;
	private SyncThread syncer;
	
	
	public ConsoleSyncer(PausableOutputStream target) {
		this.target = target;
		syncer = null;
		flushStream = new Semaphore(1);
	}
	
	public void initializeWithStream(ConcurrentCircularOutputStream source) {
		if (syncer == null) {
			syncer = new SyncThread(source, target, flushStream);
			syncer.start();
		}
	}

	@Override
	public void signalFlush() {
		flushStream.release();
	}

}