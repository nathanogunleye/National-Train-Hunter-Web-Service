package com.nathanodong.nationaltrainhunterws.service;

import com.nathanodong.nationaltrainhunterws.model.ServiceCallingPoint;
import com.nathanodong.nationaltrainhunterws.model.ServiceDeparture;
import com.nathanodong.nationaltrainhunterws.model.ServiceInformation;
import com.thalesgroup.rtti._2013_11_28.token.types.AccessToken;
import com.thalesgroup.rtti._2017_10_01.ldbsv.*;
import com.thalesgroup.rtti._2017_10_01.ldbsv.types.ServiceDetails;
import com.thalesgroup.rtti._2017_10_01.ldbsv.types.ServiceItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.xml.datatype.XMLGregorianCalendar;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class ServiceDataService {

    @Autowired
    private AccessToken accessToken;

    @Autowired
    private LDBSVServiceSoap ldbsvServiceSoap;

    private final Logger logger = LoggerFactory.getLogger(ServiceDataService.class);

    public List<ServiceDeparture> getDepartureBoard(GetBoardByCRSParams getBoardRequestParams) {
        List<ServiceDeparture> departures = new ArrayList<>();

        GetBoardResponseType departureBoard = ldbsvServiceSoap.getDepartureBoardByCRS(getBoardRequestParams, accessToken);

        logger.debug("Trains at {}", departureBoard.getGetBoardResult().getLocationName());
        logger.debug("===============================================================================");

        List<ServiceItem> serviceItems = departureBoard.getGetBoardResult().getTrainServices() != null
                ? departureBoard.getGetBoardResult().getTrainServices().getService()
                : Collections.emptyList();

        for (ServiceItem serviceItem : serviceItems) {
            logger.debug("{}", serviceItem.toString());

            ServiceDeparture serviceDeparture = new ServiceDeparture();
            serviceDeparture.setRsId(serviceItem.getRsid());
            serviceDeparture.setRid(serviceItem.getRid());
            serviceDeparture.setServiceID(serviceItem.getTrainid());
            serviceDeparture.setPlatform(serviceItem.getPlatform());

            if (serviceItem.getOrigin().getLocation().size() > 1) {
                logger.info("ServiceInformation has multiple origins.");
            }
            serviceDeparture.setOriginStation(serviceItem.getOrigin().getLocation().get(0).getLocationName());


            // TODO: Handle case when train splits at a station
            if (serviceItem.getDestination().getLocation().size() > 1) {
                logger.debug("ServiceInformation has multiple destinations.");
            }
            serviceDeparture.setDestinationStation(serviceItem.getDestination().getLocation().get(0).getLocationName());
            serviceDeparture.setVia(serviceItem.getDestination().getLocation().get(0).getVia());

            serviceDeparture.setScheduledDepartureTime(serviceItem.getStd().toGregorianCalendar().toZonedDateTime().toLocalDateTime());
            serviceDeparture.setEstimatedDepartureTime(getEstimatedDepartureTime(serviceItem));
            serviceDeparture.setDelayed(serviceDeparture.getEstimatedDepartureTime().isAfter(serviceDeparture.getScheduledDepartureTime()));
            serviceDeparture.setCancelled(serviceItem.isIsCancelled() != null ? serviceItem.isIsCancelled() : false);
            serviceDeparture.setLength(serviceItem.getLength());

            departures.add(serviceDeparture);
        }

        return departures;
    }

    public ServiceInformation getServiceDetails(GetServiceDetailsByRIDParams serviceDetailsByRIDParams) {
        ServiceInformation serviceInformation = new ServiceInformation();

        GetServiceDetailsResponseType serviceDetailsResponseType =
                ldbsvServiceSoap.getServiceDetailsByRID(serviceDetailsByRIDParams, accessToken);

        ServiceDetails serviceDetails = serviceDetailsResponseType.getGetServiceDetailsResult();

        serviceInformation.setRid(serviceDetails.getRid());
        serviceInformation.setRsId(serviceDetails.getRsid());
        serviceInformation.setOperator(serviceDetails.getOperator());
        serviceInformation.setServiceType(serviceDetails.getServiceType());

        List<ServiceCallingPoint> callingPoints = new ArrayList<>();
        serviceDetails.getLocations().getLocation()
                .stream()
                .filter(serviceLocation -> serviceLocation.isIsPass() == null)
                .forEach(serviceLocation -> {
                    ServiceCallingPoint serviceCallingPoint = new ServiceCallingPoint();

                    serviceCallingPoint.setCrs(serviceLocation.getCrs());
                    serviceCallingPoint.setStationName(serviceLocation.getLocationName());
                    serviceCallingPoint.setPlatform(serviceLocation.getPlatform());

                    serviceCallingPoint.setScheduledArrivalTime(convertToLocalDateTime(serviceLocation.getSta()));
                    serviceCallingPoint.setActualArrivalTime(convertToLocalDateTime(serviceLocation.getAta()));
                    serviceCallingPoint.setEstimatedArrivalTime(convertToLocalDateTime(serviceLocation.getEta()));

                    serviceCallingPoint.setScheduledDepartureTime(convertToLocalDateTime(serviceLocation.getStd()));
                    serviceCallingPoint.setActualDepartureTime(convertToLocalDateTime(serviceLocation.getAtd()));
                    serviceCallingPoint.setEstimatedDepartureTime(convertToLocalDateTime(serviceLocation.getEtd()));

                    callingPoints.add(serviceCallingPoint);
                });
        serviceInformation.setCallingPoints(callingPoints);

        return serviceInformation;
    }

    private LocalDateTime getEstimatedDepartureTime(ServiceItem serviceItem) {
        if (serviceItem.getEtd() != null) {
            return convertToLocalDateTime(serviceItem.getEtd());
        } else {
            return convertToLocalDateTime(serviceItem.getStd());
        }
    }

    private LocalDateTime convertToLocalDateTime(XMLGregorianCalendar time) {
        if (time == null) {
            return null;
        }

        return time.toGregorianCalendar().toZonedDateTime().toLocalDateTime();
    }
}
