package com.blindtest.service.port;

import com.blindtest.model.entity.CoverSource;
import java.io.IOException;
import java.util.List;

/** External catalogue that can suggest album covers for a track. */
public interface CoverArtProvider {

    /** Returns the source recorded on covers chosen from this provider. */
    CoverSource source();

    /** Searches covers for the query, best matches first; never returns {@code null}. */
    List<CoverArtResult> search(CoverArtQuery query) throws IOException;
}
