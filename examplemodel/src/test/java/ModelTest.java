import net.scoreworks.Department;
import net.scoreworks.University;
import net.scoreworks.treetools.TransactionManager;
import org.junit.jupiter.api.Test;

public class ModelTest {
    TransactionManager tm = TransactionManager.getInstance();

    @Test
    public void test() {
        University uni = new University();
        Department department = new Department(uni, "ExampleDepartment");
        tm.enableTransactionsForRootEntity(uni);
        University read = (University) tm.clone(uni);
    }
}
