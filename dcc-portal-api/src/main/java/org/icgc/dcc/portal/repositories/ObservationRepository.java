/*
 * Copyright 2013(c) The Ontario Institute for Cancer Research. All rights reserved.
 * 
 * This program and the accompanying materials are made available under the terms of the GNU Public
 * License v3.0. You should have received a copy of the GNU General Public License along with this
 * program. If not, see <http://www.gnu.org/licenses/>.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY
 * WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.icgc.dcc.portal.repositories;

import static org.elasticsearch.index.query.FilterBuilders.andFilter;
import static org.elasticsearch.index.query.FilterBuilders.matchAllFilter;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;

import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.client.Client;
import org.elasticsearch.index.query.AndFilterBuilder;
import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.facet.FacetBuilders;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;

import org.icgc.dcc.portal.models.Gene;
import org.icgc.dcc.portal.models.Observation;
import org.icgc.dcc.portal.request.RequestSearchQuery;
import org.icgc.dcc.portal.services.FilterService;

public class ObservationRepository extends BaseRepository {

  @Inject
  public ObservationRepository(Client client) {
    super(client, Observation.INDEX, Observation.TYPE, Observation.FIELDS);
  }

  @Override
  QueryBuilder buildQuery() {
    return matchAllQuery();
  }

  @Override
  FilterBuilder buildScoreFilters(JsonNode filters) {
    if (filters.has(Gene.NAME)) {
      AndFilterBuilder scoreFilters = andFilter();
      scoreFilters.add(FilterService.buildNestedFilter(Gene.NAME,
          FilterService.buildAndFilters(Gene.FILTERS, filters.get(Gene.NAME))));
      return scoreFilters;
    }

    return matchAllFilter();
  }

  @Override
  FilterBuilder buildFilters(JsonNode filters) {
    AndFilterBuilder observationFilters = andFilter();

    if (filters.has(Observation.NAME)) {
      observationFilters.add(FilterService.buildAndFilters(Observation.FILTERS, filters.get(Observation.NAME)));
    }
    if (filters.has(Gene.NAME)) {
      observationFilters.add(FilterService.buildNestedFilter(Gene.NAME,
          FilterService.buildAndFilters(Gene.FILTERS, filters.get(Gene.NAME))));
    }
    if (filters.has(Observation.NAME)) {
      observationFilters.add(FilterService.buildNestedFilter(Observation.NAME,
          FilterService.buildAndFilters(Observation.FILTERS, filters.get(Observation.NAME))));
    }
    return observationFilters;
  }

  @Override
  SearchRequestBuilder addFacets(SearchRequestBuilder s, RequestSearchQuery requestSearchQuery) {
    for (String facet : Observation.FACETS.get("terms")) {
      s.addFacet(FacetBuilders.termsFacet(facet) //
          .field(facet) //
          .facetFilter(setFacetFilter(Observation.NAME, facet, requestSearchQuery.getFilters())) //
          .size(Integer.MAX_VALUE) //
          .global(true));
    }

    return s;
  }
}
