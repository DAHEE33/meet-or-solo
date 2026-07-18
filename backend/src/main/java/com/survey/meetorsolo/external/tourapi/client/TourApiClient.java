package com.survey.meetorsolo.external.tourapi.client;

import com.survey.meetorsolo.external.tourapi.dto.SearchFestivalItem;
import com.survey.meetorsolo.external.tourapi.dto.SearchFestivalRequest;
import com.survey.meetorsolo.external.tourapi.dto.TourApiPage;

public interface TourApiClient {

    TourApiPage<SearchFestivalItem> searchFestivals(SearchFestivalRequest request);
}
