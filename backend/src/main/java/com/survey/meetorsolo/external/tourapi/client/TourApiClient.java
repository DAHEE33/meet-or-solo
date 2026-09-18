package com.survey.meetorsolo.external.tourapi.client;

import com.survey.meetorsolo.external.tourapi.dto.DetailCommonItem;
import com.survey.meetorsolo.external.tourapi.dto.DetailInfoItem;
import com.survey.meetorsolo.external.tourapi.dto.DetailIntroFestivalItem;
import com.survey.meetorsolo.external.tourapi.dto.FestivalDetailApiRequest;
import com.survey.meetorsolo.external.tourapi.dto.SearchFestivalItem;
import com.survey.meetorsolo.external.tourapi.dto.SearchFestivalRequest;
import com.survey.meetorsolo.external.tourapi.dto.SearchTourPlaceItem;
import com.survey.meetorsolo.external.tourapi.dto.SearchTourPlaceRequest;
import com.survey.meetorsolo.external.tourapi.dto.TourApiPage;
import java.util.List;
import java.util.Optional;

public interface TourApiClient {

    TourApiPage<SearchFestivalItem> searchFestivals(SearchFestivalRequest request);

    TourApiPage<SearchTourPlaceItem> searchTourPlaces(SearchTourPlaceRequest request);

    Optional<DetailCommonItem> fetchDetailCommon(FestivalDetailApiRequest request);

    Optional<DetailIntroFestivalItem> fetchDetailIntroFestival(FestivalDetailApiRequest request);

    List<DetailInfoItem> fetchDetailInfo(FestivalDetailApiRequest request);
}
