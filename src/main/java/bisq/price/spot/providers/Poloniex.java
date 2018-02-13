/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.price.spot.providers;

import bisq.price.spot.ExchangeRate;
import bisq.price.spot.ExchangeRateProvider;
import bisq.price.util.Altcoins;

import io.bisq.network.http.HttpClient;

import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.poloniex.dto.marketdata.PoloniexMarketData;
import org.knowm.xchange.poloniex.dto.marketdata.PoloniexTicker;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Duration;

import java.io.IOException;
import java.io.UncheckedIOException;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@Order(4)
public class Poloniex extends ExchangeRateProvider {

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = new HttpClient("https://poloniex.com/public");

    public Poloniex() {
        super("POLO", "poloniex", Duration.ofMinutes(1));
    }

    @Override
    public Set<ExchangeRate> doGet() {
        Date timestamp = new Date(); // Poloniex tickers don't include their own timestamp

        return getTickers()
            .filter(t -> t.getCurrencyPair().base.equals(Currency.BTC))
            .filter(t -> Altcoins.ALL_SUPPORTED.contains(t.getCurrencyPair().counter.getCurrencyCode()))
            .map(t ->
                new ExchangeRate(
                    t.getCurrencyPair().counter.getCurrencyCode(),
                    t.getPoloniexMarketData().getLast(),
                    timestamp,
                    this.getName()
                )
            )
            .collect(Collectors.toSet());
    }

    private Stream<PoloniexTicker> getTickers() {

        return getTickerMapEntries()
            .map(e -> {
                String pair = e.getKey();
                PoloniexMarketData data = e.getValue();
                String[] symbols = pair.split("_"); // e.g. BTC_USD => [BTC, USD]
                return new PoloniexTicker(data, new CurrencyPair(symbols[0], symbols[1]));
            });
    }

    private Stream<Map.Entry<String, PoloniexMarketData>> getTickerMapEntries() {
        TypeReference typeReference = new TypeReference<HashMap<String, PoloniexMarketData>>() {
        };
        try {
            String json = httpClient.requestWithGET("?command=returnTicker", "User-Agent", "");
            Map<String, PoloniexMarketData> tickers = mapper.readValue(json, typeReference);
            return tickers.entrySet().stream();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
