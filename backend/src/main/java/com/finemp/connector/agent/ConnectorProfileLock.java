package com.finemp.connector.agent;

import java.nio.channels.*;
import java.nio.file.*;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** OS-held lock, scoped to the canonical local service-account directory. Empty lock files are never deleted. */
final class ConnectorProfileLock implements AutoCloseable {
    private final FileChannel channel;
    private final FileLock lock;
    private final AtomicBoolean closed=new AtomicBoolean();
    private ConnectorProfileLock(FileChannel channel,FileLock lock){this.channel=channel;this.lock=lock;}
    static ConnectorProfileLock acquire(Path directory,UUID source){
        FileChannel channel=null;
        try {
            if(directory==null||source==null)throw invalid();
            Files.createDirectories(directory);
            if(Files.isSymbolicLink(directory)||!Files.isDirectory(directory,LinkOption.NOFOLLOW_LINKS))throw invalid();
            var canonical=directory.toRealPath();var file=canonical.resolve(source+".lock");
            channel=FileChannel.open(file,StandardOpenOption.CREATE,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);
            var lock=channel.tryLock();if(lock==null)throw invalid();
            return new ConnectorProfileLock(channel,lock);
        }catch(IOException|RuntimeException unavailable){if(channel!=null)try{channel.close();}catch(IOException ignored){}throw invalid();}
    }
    @Override public void close(){if(closed.compareAndSet(false,true)){try{lock.release();}catch(IOException ignored){}try{channel.close();}catch(IOException ignored){}}}
    private static ConnectorClientException invalid(){return new ConnectorClientException(ConnectorClientException.Kind.CONFIGURATION);}
    @Override public String toString(){return "ConnectorProfileLock[redacted]";}
}
