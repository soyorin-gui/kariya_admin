package org.lbl.intranet.operation.log.model;

import java.io.InputStream;

public record LogDownload(String fileName, String contentType, long contentLength, InputStream content) { }
