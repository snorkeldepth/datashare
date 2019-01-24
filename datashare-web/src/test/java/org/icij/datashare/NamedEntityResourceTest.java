package org.icij.datashare;

import net.codestory.http.WebServer;
import net.codestory.http.filters.basic.BasicAuthFilter;
import net.codestory.http.misc.Env;
import net.codestory.rest.FluentRestTest;
import org.icij.datashare.session.HashMapUser;
import org.icij.datashare.session.LocalUserFilter;
import org.icij.datashare.text.NamedEntity;
import org.icij.datashare.text.indexing.Indexer;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.io.IOException;
import java.util.stream.Stream;

import static java.lang.Boolean.parseBoolean;
import static java.util.Collections.singletonList;
import static org.fest.assertions.Assertions.assertThat;
import static org.icij.datashare.text.Language.FRENCH;
import static org.icij.datashare.text.NamedEntity.Category.PERSON;
import static org.icij.datashare.text.NamedEntity.create;
import static org.icij.datashare.text.nlp.Pipeline.Type.CORENLP;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

public class NamedEntityResourceTest implements FluentRestTest {
    @Mock
    Indexer indexer;
     private static WebServer server = new WebServer() {
         @Override
         protected Env createEnv() {
             return Env.prod();
         }
     }.startOnRandomPort();

    @Before
    public void setUp() throws Exception {
        initMocks(this);
        server.configure(routes -> routes.add(new NamedEntityResource(indexer)).filter(LocalUserFilter.class));
    }

    @Test
    public void test_get_standalone_named_entity_should_return_not_found() {
        get("/api/namedEntity/my_id").should().respond(404);
    }

    @Test
    public void test_get_named_entity() {
        NamedEntity toBeReturned = create(PERSON, "mention", 123, "docId", CORENLP, FRENCH);
        doReturn(toBeReturned).when(indexer).get("local-datashare", "my_id", "root_parent");
        get("/api/namedEntity/my_id?routing=root_parent").should().respond(200).haveType("application/json").contain(toBeReturned.getId());
    }

    @Test
    public void test_get_named_entity_in_prod_mode() {
        server.configure(routes -> routes.add(new NamedEntityResource(indexer)).filter(new BasicAuthFilter("/", "icij", HashMapUser.singleUser("anne"))));
        NamedEntity toBeReturned = create(PERSON, "mention", 123, "docId", CORENLP, FRENCH);
        doReturn(toBeReturned).when(indexer).get("anne-datashare", "my_id", "root_parent");

        get("/api/namedEntity/my_id?routing=root_parent").withAuthentication("anne", "notused").
                should().respond(200).haveType("application/json").contain(toBeReturned.getId());
    }

    @Test
    public void test_hide_named_entity_when_success() throws IOException {
        NamedEntity toBeHidden = create(PERSON, "to_update", 123, "docId", CORENLP, FRENCH);
        assertThat(toBeHidden.isHidden()).isFalse();
        Indexer.Searcher searcher = mock(Indexer.Searcher.class);
        doReturn(Stream.of(toBeHidden)).when(searcher).execute();
        doReturn(searcher).when(searcher).withFieldValue(any(), any());
        doReturn(searcher).when(indexer).search("local-datashare", NamedEntity.class);

        put("/api/namedEntity/hide/to_update").should().respond(200);

        verify(indexer).bulkUpdate("local-datashare", singletonList(toBeHidden));
    }

    @Test
    public void test_hide_named_entity_when_failure() throws IOException {
        doThrow(new RuntimeException()).when(indexer).search("local-datashare", NamedEntity.class);

        put("/api/namedEntity/hide/to_update").should().respond(500);
    }

    @Test
    public void test_sync_models() {
        put("/api/namedEntity/syncModels/true").should().respond(200);
        assertThat(parseBoolean(System.getProperty("DS_SYNC_NLP_MODELS"))).isTrue();

        put("/api/namedEntity/syncModels/false").should().respond(200);
        assertThat(parseBoolean(System.getProperty("DS_SYNC_NLP_MODELS"))).isFalse();
    }

    @Override
    public int port() {
        return server.port();
    }
}
