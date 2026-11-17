package com.pikachew.dangUBI.batch.ingest.dto;

import java.util.List;

import com.pikachew.dangUBI.domain.trip.model.MicrotripSnapshot;
import com.pikachew.dangUBI.domain.trip.model.TripSnapshot;

/** gz 파일 하나를 해석한 결과. 해석하지 못한 줄은 건너뛰고 skippedLineCount로만 남긴다. */
public record ParsedTelemetryFile(
		String objectKey,
		List<TripSnapshot> trips,
		List<MicrotripSnapshot> microtrips,
		int skippedLineCount
) {

	public ParsedTelemetryFile {
		trips = List.copyOf(trips);
		microtrips = List.copyOf(microtrips);
	}

}
