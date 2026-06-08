package com.worldwide.paymentgateway.acquirer.factory;

import com.worldwide.paymentgateway.acquirer.AcquirerConnector;
import com.worldwide.paymentgateway.domain.enums.AcquirerType;
import com.worldwide.paymentgateway.exception.PaymentException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class AcquirerConnectorFactory {

    private final Map<AcquirerType, AcquirerConnector> connectors;

    public AcquirerConnectorFactory(java.util.List<AcquirerConnector> connectorList) {
        this.connectors = connectorList.stream()
                .collect(Collectors.toMap(AcquirerConnector::getType, Function.identity()));
    }

    public AcquirerConnector get(AcquirerType type) {
        AcquirerConnector connector = connectors.get(type);
        if (connector == null) {
            throw new PaymentException("CONNECTOR_NOT_FOUND",
                    "No connector registered for acquirer: " + type, HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return connector;
    }
}
