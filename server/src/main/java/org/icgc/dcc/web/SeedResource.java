package org.icgc.dcc.web;

import java.io.IOException;
import java.io.InputStream;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.io.IOUtils;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.icgc.dcc.core.model.Project;
import org.icgc.dcc.core.model.User;
import org.icgc.dcc.dictionary.DictionaryService;
import org.icgc.dcc.dictionary.model.CodeList;
import org.icgc.dcc.dictionary.model.Dictionary;
import org.icgc.dcc.filesystem.DccFileSystem;
import org.icgc.dcc.release.model.Release;

import com.google.code.morphia.Datastore;
import com.google.inject.Inject;

@Path("seed")
public class SeedResource {

  @Context
  HttpHeaders requestHeaders;

  @Inject
  private Datastore datastore;

  @Inject
  private DictionaryService dictionaryService;

  @Inject
  private DccFileSystem dccfs;

  @POST
  @Path("users")
  public Response seedUsers(User[] users, @DefaultValue("false") @QueryParam("delete") boolean delete) {
    if(delete) {
      this.datastore.getCollection(User.class).drop();
    }
    this.datastore.save(users);
    return Response.status(Status.CREATED).build();
  }

  @POST
  @Path("projects")
  public Response seedProjects(Project[] projects, @DefaultValue("false") @QueryParam("delete") boolean delete) {
    if(delete) {
      this.datastore.getCollection(Project.class).drop();
    }
    this.datastore.save(projects);
    return Response.status(Status.CREATED).build();
  }

  @POST
  @Path("releases")
  public Response seedReleases(Release[] releases, @DefaultValue("false") @QueryParam("delete") boolean delete) {
    if(delete) {
      this.datastore.getCollection(Release.class).drop();
    }
    this.datastore.save(releases);
    return Response.status(Status.CREATED).build();
  }

  @POST
  @Path("dictionaries")
  public Response seedDictionaries(Dictionary[] dictionaries,
      @DefaultValue("false") @QueryParam("delete") boolean delete) {
    if(delete) {
      this.datastore.getCollection(Dictionary.class).drop();
    }
    this.datastore.save(dictionaries);
    return Response.status(Status.CREATED).build();
  }

  @POST
  @Path("codelists")
  public Response seedCodeLists(CodeList[] codelists, @DefaultValue("false") @QueryParam("delete") boolean delete) {
    if(delete) {
      this.datastore.getCollection(CodeList.class).drop();
    }
    this.datastore.save(codelists);
    return Response.status(Status.CREATED).build();
  }

  @POST
  @Path("fs/{filepath: .*}")
  public Response seedFileSystem(@PathParam("filepath") String filename, InputStream fileContents) {
    FileSystem fs = this.dccfs.getFileSystem();
    org.apache.hadoop.fs.Path destinationPath =
        new org.apache.hadoop.fs.Path(dccfs.getRootStringPath() + "/" + filename);

    try {
      FSDataOutputStream fileDestination = fs.create(destinationPath);

      IOUtils.copy(fileContents, fileDestination);
      fileDestination.flush();
      fileDestination.close();
    } catch(IOException e) {
      return Response.status(Status.INTERNAL_SERVER_ERROR).build();
    }

    return Response.status(Status.CREATED).build();
  }
}
