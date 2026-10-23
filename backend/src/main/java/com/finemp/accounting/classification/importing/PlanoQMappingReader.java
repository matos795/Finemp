package com.finemp.accounting.classification.importing;

import java.io.InputStream;
import java.util.List;

public interface PlanoQMappingReader {

    List<PlanoQMapping> read(InputStream inputStream);
}