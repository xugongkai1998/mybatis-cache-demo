package mapper;

import entity.StudentEntity;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

public class StudentMapperTest {

    private SqlSessionFactory factory;

    @Before
    public void setUp() throws Exception {
        factory = new SqlSessionFactoryBuilder().build(Resources.getResourceAsReader("mybatis-config.xml"));

    }

    @Test
    public void showDefaultCacheConfiguration() {
        System.out.println("本地缓存范围: " + factory.getConfiguration().getLocalCacheScope());
        System.out.println("二级缓存是否被启用: " + factory.getConfiguration().isCacheEnabled());
    }

    /**
     * 同一sqlSession, 第一次查询走DB, 后续走缓存;
     * <setting name="localCacheScope" value="SESSION"/>
     * <setting name="cacheEnabled" value="true"/>
     * @throws Exception
     */
    @Test
    public void testLocalCache() throws Exception {
        SqlSession sqlSession = factory.openSession(true); // 自动提交事务
        StudentMapper studentMapper = sqlSession.getMapper(StudentMapper.class);

        System.out.println(studentMapper.getStudentById(1));
        System.out.println(studentMapper.getStudentById(1));
        System.out.println(studentMapper.getStudentById(1));

        sqlSession.close();
    }

    /**
     * 同一SqlSession, 第一次查询走DB, 第二次走缓存, 第三次执行更新,此时缓存失效, 因此后续查询(第4次)走DB
     * <setting name="localCacheScope" value="SESSION"/>
     * <setting name="cacheEnabled" value="true"/>
     * @throws Exception
     */
    @Test
    public void testLocalCacheClear() throws Exception {
        SqlSession sqlSession = factory.openSession(true); // 自动提交事务
        StudentMapper studentMapper = sqlSession.getMapper(StudentMapper.class);

        System.out.println(studentMapper.getStudentById(1));
        System.out.println(studentMapper.getStudentById(1));
        System.out.println("增加了" + studentMapper.addStudent(buildStudent()) + "个学生\n");
        System.out.println(studentMapper.getStudentById(1));

        sqlSession.close();
    }

    /**
     * 开启两个SqlSession, 操作同一张表 <br/>
     * 会话1先查询数据，并加入会话1缓存; （id=1, name=点点） <br/>
     * 会话2更新id=1的数据, 点点->小岑;  <br/>
     * 会话2执行查询,能得到正确数据: 小岑  <br/>
     * 会话1执行查询, 得到脏数据: 点点    <br/>
     *  <setting name="localCacheScope" value="SESSION"/>
     *  <setting name="cacheEnabled" value="true"/>
     * @throws Exception
     */
    @Test
    public void testLocalCacheScope() throws Exception {
        SqlSession sqlSession1 = factory.openSession(true); // 自动提交事务
        SqlSession sqlSession2 = factory.openSession(true); // 自动提交事务
       StudentMapper m1 = sqlSession1.getMapper(StudentMapper.class);
       StudentMapper m2 = sqlSession2.getMapper(StudentMapper.class);

        System.out.println("会话1，读取数据: " + m1.getStudentById(1));
        System.out.println("会话1，读取数据: " + m1.getStudentById(1));

        System.out.println("会话2，更新了" + m2.updateStudentName("小岑", 1) + "个学生的数据\n");

        System.out.println("会话1，读取数据: " + m1.getStudentById(1)); // 脏数据,  除非主动清空会话1的缓存:sqlSession1.clearCache();此时会话1将从DB查询

        System.out.println("会话2，读取数据: " + m2.getStudentById(1));


        // 恢复为原始数据状态，不影响其他单元测试
        m2.updateStudentName("点点", 1);
    }


    private StudentEntity buildStudent(){
        StudentEntity studentEntity = new StudentEntity();
        studentEntity.setName("明明");
        studentEntity.setAge(20);
        return studentEntity;
    }

    /**
     * 使用自动事务,没有调用commit, 二级缓存不生效 <br/>
     * 两次查询均从DB查询 <br/>
     *  <setting name="localCacheScope" value="SESSION"/>
     *  <setting name="cacheEnabled" value="true"/>
     * @throws Exception
     */
    @Test
    public void testCacheWithoutCommitOrClose() throws Exception {
        SqlSession s1 = factory.openSession(false); // 自动提交事务
        SqlSession s2 = factory.openSession(false); // 自动提交事务

        StudentMapper m1 = s1.getMapper(StudentMapper.class);
        StudentMapper m2 = s2.getMapper(StudentMapper.class);

        System.out.println("会话1读取数据: " + m1.getStudentById(1));
        System.out.println("会话2读取数据: " + m2.getStudentById(1));

    }

    /**
     *  使用自动事务，有使用commit，信息可以被缓存 <br/>
     *  会话1查询数据,并commit, 数据被缓存, <br/>
     *  会话2查询时，走缓存。<br/>
     *  <setting name="localCacheScope" value="SESSION"/>
     *  <setting name="cacheEnabled" value="true"/>
     * @throws Exception
     */
    @Test
    public void testCacheWithCommitOrClose() throws Exception {
        SqlSession s1 = factory.openSession(false); // 自动提交事务
        SqlSession s2 = factory.openSession(false); // 自动提交事务

        StudentMapper m1 = s1.getMapper(StudentMapper.class);
        StudentMapper m2 = s2.getMapper(StudentMapper.class);

        System.out.println("会话1读取数据: " + m1.getStudentById(1));
        s1.commit();
        System.out.println("会话2读取数据: " + m2.getStudentById(1));

    }

    /**
     *  会话1查询数据并commit，数据被缓存；<br/>
     *  会话2查询数据时，使用缓存 <br/>
     *  会话3修改数据 <br/>
     *  会话2再次查询，此时缓存失效，从DB获取。
     *  <setting name="localCacheScope" value="SESSION"/>
     *  <setting name="cacheEnabled" value="true"/>
     * @throws Exception
     */
    @Test
    public void testCacheWithUpdate() throws Exception {
        SqlSession s1 = factory.openSession(true); // 自动提交事务
        SqlSession s2 = factory.openSession(true); // 自动提交事务
        SqlSession s3 = factory.openSession(true); // 自动提交事务


        StudentMapper m1 = s1.getMapper(StudentMapper.class);
        StudentMapper m2 = s2.getMapper(StudentMapper.class);
        StudentMapper m3 = s3.getMapper(StudentMapper.class);


        System.out.println("会话1读取数据: " + m1.getStudentById(1));
        s1.close();
        System.out.println("会话2读取数据: " + m2.getStudentById(1));

        // 会话3更新数据
        m3.updateStudentName("方方",1);
        s3.commit();
        System.out.println("会话3更新数据完成\n");

        System.out.println("会话2读取数据: " + m2.getStudentById(1));

        // 恢复为原始数据状态，不影响其他单元测试
        m2.updateStudentName("点点", 1);
    }

    /**
     *  由于MyBatis的二级缓存是基于namespace的，多表查询语句所在的namspace无法感应到其他namespace中的语句对多表查询中涉及的表进行的修改，引发脏数据问题 <br/>
     *  本测试用例，mapper1，mapper2属于不同命名空间，因此缓存空间也是不同的 <br/>
     *  会话1，studentMapper读取student, class表，并缓存 <br/>
     *  会话2, 读取缓存数据 <br/>
     *  ClassMapper, 更新班级信息 <br/>
     *  会话2, 无法感知到classMapper执行的更新，读取到脏数据；<br/>
     *  <setting name="localCacheScope" value="SESSION"/>
     *  <setting name="cacheEnabled" value="true"/>
     * @throws Exception
     */
    @Test
    public void testCacheWithDiffererntNamespace() throws Exception {
        SqlSession s1 = factory.openSession(true); // 自动提交事务
        SqlSession s2 = factory.openSession(true); // 自动提交事务
        SqlSession s3 = factory.openSession(true); // 自动提交事务

        // 启动该单元测试前，
        // 注释mapper/ClassMapper.xml中的 <cache-ref namespace="mapper.StudentMapper"/>
        // 取消注释mapper/ClassMapper.xml：<cache/>

        StudentMapper m1 = s1.getMapper(StudentMapper.class);
        StudentMapper m2 = s2.getMapper(StudentMapper.class);
        ClassMapper classMapper = s3.getMapper(ClassMapper.class);

        System.out.println("会话1读取数据: " + m1.getStudentByIdWithClassInfo(1));
        s1.close();

        System.out.println("会话2读取数据: " + m2.getStudentByIdWithClassInfo(1));

        classMapper.updateClassName("特色一班",1);
        s3.commit();
        System.out.println("ClassMapper更新数据完成\n");

        System.out.println("会话2读取数据: " + m2.getStudentByIdWithClassInfo(1)); //脏数据，无法感知到classMapper的改动

        // 恢复为原始数据状态，不影响其他单元测试
        classMapper.updateClassName("一班", 1);
    }

    /**
     *  由于MyBatis的二级缓存是基于namespace的，多表查询语句所在的namspace无法感应到其他namespace中的语句对多表查询中涉及的表进行的修改，<br/>
     *  引发脏数据问题 本测试用例，mapper1，mapper2属于不同命名空间，因此缓存空间也是不同的 <br/>
     *  会话1，studentMapper读取student, class表，并缓存 <br/>
     *  会话2, 读取缓存数据 <br/>
     *  ClassMapper, 更新班级信息 ，由于studentMapper/classMapper属于同一个缓存空间，此时执行了更新，旧的缓存数据将失效<br/>
     *  会话2, 从DB获取数据 <br/>
     *  <setting name="localCacheScope" value="SESSION"/>
     *  <setting name="cacheEnabled" value="true"/>
     * @throws Exception
     */
    @Test
    public void testCacheWithDiffererntNamespaceWithCacheRef() throws Exception {
        SqlSession s1 = factory.openSession(true); // 自动提交事务
        SqlSession s2 = factory.openSession(true); // 自动提交事务
        SqlSession s3 = factory.openSession(true); // 自动提交事务

        // 启动该单元测试前，
        // 取消注释mapper/ClassMapper.xml中的 <cache-ref namespace="mapper.StudentMapper"/>
        // 注释mapper/ClassMapper.xml：<cache/>

        StudentMapper m1 = s1.getMapper(StudentMapper.class);
        StudentMapper m2 = s2.getMapper(StudentMapper.class);
        ClassMapper classMapper = s3.getMapper(ClassMapper.class);

        System.out.println("会话1读取数据: " + m1.getStudentByIdWithClassInfo(1));
        s1.close();

        System.out.println("会话2读取数据: " + m2.getStudentByIdWithClassInfo(1));

        classMapper.updateClassName("特色一班",1);
        s3.commit();
        System.out.println("ClassMapper更新数据完成\n");

        System.out.println("会话2读取数据: " + m2.getStudentByIdWithClassInfo(1)); //脏数据，无法感知到classMapper的改动

        // 恢复为原始数据状态，不影响其他单元测试
        classMapper.updateClassName("一班", 1);
    }


}