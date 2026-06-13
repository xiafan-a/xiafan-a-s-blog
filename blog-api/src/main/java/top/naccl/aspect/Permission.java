package top.naccl.aspect;
import top.naccl.annotation.testPermission;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class Permission {
    @Before("@annotation(permission)")
    public  void test(testPermission permission){
        System.out.println(permission.value());
    }
}
