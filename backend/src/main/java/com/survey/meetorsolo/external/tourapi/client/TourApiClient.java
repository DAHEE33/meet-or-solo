package com.survey.meetorsolo.external.tourapi.client;

import com.survey.meetorsolo.external.tourapi.dto.SearchFestivalItem;
import com.survey.meetorsolo.external.tourapi.dto.SearchFestivalRequest;
import com.survey.meetorsolo.external.tourapi.dto.SearchTourPlaceItem;
import com.survey.meetorsolo.external.tourapi.dto.SearchTourPlaceRequest;
import com.survey.meetorsolo.external.tourapi.dto.TourApiPage;

public interface TourApiClient {

    TourApiPage<SearchFestivalItem> searchFestivals(SearchFestivalRequest request);

    TourApiPage<SearchTourPlaceItem> searchTourPlaces(SearchTourPlaceRequest request);
}
