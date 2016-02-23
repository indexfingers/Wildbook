package org.ecocean.rest;

import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

import org.ecocean.Individual;
import org.ecocean.encounter.EncounterFactory;
import org.ecocean.media.MediaAssetFactory;
import org.ecocean.media.MediaAssetType;
import org.ecocean.servlet.ServletUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.samsix.database.Database;
import com.samsix.database.DatabaseException;
import com.samsix.database.SqlRelationType;
import com.samsix.database.SqlStatement;

@RestController
@RequestMapping(value = "/obj/individual")
public class IndividualController {
    @RequestMapping(value = "save", method = RequestMethod.POST)
    public Individual saveEncounter(final HttpServletRequest request,
                                    @RequestBody @Valid final Individual individual) throws DatabaseException {
        if (individual == null) {
            return null;
        }

        try (Database db = ServletUtils.getDb(request)) {
            db.performTransaction(() -> {
                EncounterFactory.saveIndividual(db, individual);
            });
            return individual;
        }
    }

    @RequestMapping(value = "photos/{id}", method = RequestMethod.GET)
    public List<SimplePhoto> getPhotos(final HttpServletRequest request,
                                @PathVariable("id")
                                final int id) throws DatabaseException {

        try (Database db = ServletUtils.getDb(request)) {
            SqlStatement sql = new SqlStatement(MediaAssetFactory.TABLENAME_MEDIAASSET,
                    MediaAssetFactory.ALIAS_MEDIAASSET,
                    MediaAssetFactory.ALIAS_MEDIAASSET + ".*");
                    sql.addInnerJoin(MediaAssetFactory.ALIAS_MEDIAASSET, MediaAssetFactory.PK_MEDIAASSET, "encounter_media", "em", "mediaid");
                    sql.addInnerJoin("em", "encounterid", "encounters", "e", "encounterid");
                    sql.addCondition(MediaAssetFactory.ALIAS_MEDIAASSET,
                    "type",
                    SqlRelationType.EQUAL,
                    MediaAssetType.IMAGE.getCode());
                    sql.addCondition("e", "individualid", SqlRelationType.EQUAL, id);

                    return db.selectList(sql, (rs) -> {
                        return MediaAssetFactory.readPhoto(rs);
                    });
        }
    }
}