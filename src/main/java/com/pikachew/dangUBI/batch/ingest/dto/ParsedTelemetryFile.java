package com.pikachew.dangUBI.batch.ingest.dto;

import java.util.List;

import com.pikachew.dangUBI.domain.telemetry.model.MicrotripRecord;
import com.pikachew.dangUBI.domain.telemetry.model.TripRecord;

/** gz 파일 하나를 해석한 결과. 해석하지 못한 줄은 건너뛰고 skippedLineCount로만 남긴다. */
public record ParsedTelemetryFile(
		String objectKey,
		List<TripRecord> trips,
		List<MicrotripRecord> microtrips,
		int skippedLineCount
) {

	public ParsedTelemetryFile {
		trips = List.copyOf(trips);
		microtrips = List.copyOf(microtrips);
	}

}
