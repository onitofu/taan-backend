package taan.online.inventory.repository;

import java.util.List;
import taan.online.inventory.model.ItemDefinition;

public interface ItemDefinitionRepository {
    List<ItemDefinition> load();
}
