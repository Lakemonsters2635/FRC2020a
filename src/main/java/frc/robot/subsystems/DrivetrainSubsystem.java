/*----------------------------------------------------------------------------*/
/* Copyright (c) 2018 FIRST. All Rights Reserved.                             */
/* Open Source Software - may be modified and shared by FRC teams. The code   */
/* must be accompanied by the FIRST BSD license file in the root directory of */
/* the project.                                                               */
/*----------------------------------------------------------------------------*/

package frc.robot.subsystems;

import edu.wpi.first.wpilibj.AnalogInput;
import edu.wpi.first.wpilibj.Spark;
import edu.wpi.first.wpilibj.command.Subsystem;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import com.revrobotics.CANSparkMaxLowLevel;

import org.frcteam2910.common.control.*;
import org.frcteam2910.common.drivers.Gyroscope;
import org.frcteam2910.common.drivers.SwerveModule;
import org.frcteam2910.common.math.RigidTransform2;
import org.frcteam2910.common.math.Rotation2;
import org.frcteam2910.common.math.Vector2;
import org.frcteam2910.common.robot.drivers.NavX;
import org.frcteam2910.common.robot.subsystems.SwerveDrivetrain;
import org.frcteam2910.common.util.DrivetrainFeedforwardConstants;
import org.frcteam2910.common.util.HolonomicDriveSignal;
import org.frcteam2910.common.util.HolonomicFeedforward;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Optional;

//import frc.lib.motion_profiling.Path2D;
import frc.robot.Mk2SwerveModule;
import frc.robot.Robot;
import frc.robot.RobotMap;
import frc.robot.commands.HolonomicDriveCommand;

import com.kauailabs.navx.frc.AHRS;
import com.revrobotics.CANSparkMax;
import edu.wpi.first.wpilibj.SPI;




/**
 * Add your docs here.
 */
public class DrivetrainSubsystem extends SwerveDrivetrain {
  private static final double TRACKWIDTH = 21.0;
  private static final double WHEELBASE = 25.0;

  public static final ITrajectoryConstraint[] CONSTRAINTS = {
      //Original
        //   new MaxVelocityConstraint(12.0 * 12.0),
        //   new MaxAccelerationConstraint(15.0 * 12.0),                                 
        //   new CentripetalAccelerationConstraint(25.0 * 12.0)

        new MaxVelocityConstraint(12.0 * 3.0),
          new MaxAccelerationConstraint(15.0 * 3.0),                                 
          new CentripetalAccelerationConstraint(25.0 * 3.0)
  };    
  
  public static final ITrajectoryConstraint[] INTAKE_CONSTRAINTS = {
    //Original
      //   new MaxVelocityConstraint(12.0 * 12.0),
      //   new MaxAccelerationConstraint(15.0 * 12.0),                                 
      //   new CentripetalAccelerationConstraint(25.0 * 12.0)

      new MaxVelocityConstraint(10.0 * 2.0),
        new MaxAccelerationConstraint(12.0 * 2.0),                                 
        new CentripetalAccelerationConstraint(20.0 * 2.0)
};  


  //Properties used for Path following.
  private static Vector2 prevPosition = new Vector2(0.0D, 0.0D);
  private static Vector2 prevPathPosition = new Vector2(0.0D, 0.0D);
  private static double prevTime;
  private static double prevPathHeading;
  //private val poseHistory = InterpolatingTreeMap<InterpolatingDouble, SwerveDrive.Pose>(75)


//   FL -38.709162
//   FR -331.565027
//   BL -347.648226
//   BR -284.570943

//BUNNYBOT
//   private static final double BACK_RIGHT_ANGLE_OFFSET_COMPETITION = Math.toRadians(-284.570943); //272   -281
//   private static final double BACK_LEFT_ANGLE_OFFSET_COMPETITION = Math.toRadians(-347.648226); //346   -346
//   private static final double FRONT_RIGHT_ANGLE_OFFSET_COMPETITION = Math.toRadians(-331.565027); //331   -331
//   private static final double FRONT_LEFT_ANGLE_OFFSET_COMPETITION = Math.toRadians(-38.709162); //37   -37

//Kinda Working Values for Comp Bot
// private static final double BACK_RIGHT_ANGLE_OFFSET_COMPETITION = Math.toRadians(-99.34); //272   -281  99.339977
//   private static final double BACK_LEFT_ANGLE_OFFSET_COMPETITION = Math.toRadians(-138.7+180-10); //346   -346   46.958537     138.7
//   private static final double FRONT_RIGHT_ANGLE_OFFSET_COMPETITION = Math.toRadians(-12.57); //331   -331 12.572081
//   private static final double FRONT_LEFT_ANGLE_OFFSET_COMPETITION = Math.toRadians(-300.5+180-28); //37   -37    104.136930    300.5
  

//NEGATE SMARTDASHBOARD VALUES IN OFFSEST
//COMP BOT
private static final double BACK_RIGHT_ANGLE_OFFSET_COMPETITION = Math.toRadians(-29.9); //95.7
private static final double BACK_LEFT_ANGLE_OFFSET_COMPETITION = Math.toRadians(-138.8); //-148 + 180   -326.8
private static final double FRONT_RIGHT_ANGLE_OFFSET_COMPETITION = Math.toRadians(-173.1); //14
private static final double FRONT_LEFT_ANGLE_OFFSET_COMPETITION = Math.toRadians(-24.5); //-336+180    -151.6

  private static final PidConstants FOLLOWER_TRANSLATION_CONSTANTS = new PidConstants(0.05, 0.01, 0.0);
  private static final PidConstants FOLLOWER_ROTATION_CONSTANTS = new PidConstants(0.3, 0.01, 0.0);//0.3, 0.1, 0.0
  private static final HolonomicFeedforward FOLLOWER_FEEDFORWARD_CONSTANTS = new HolonomicFeedforward(
          new DrivetrainFeedforwardConstants(1.0 / (14.0 * 12.0), 0.0, 0.0)
  );

 //fix this
  private NavX navX = new NavX(SPI.Port.kMXP);
  
  private static final PidConstants SNAP_ROTATION_CONSTANTS = new PidConstants(0.3, 0.01, 0.0);

  //private static final DrivetrainSubsystem instance = new DrivetrainSubsystem();

  private SwerveModule[] swerveModules;

  private HolonomicMotionProfiledTrajectoryFollower follower = new HolonomicMotionProfiledTrajectoryFollower(
          FOLLOWER_TRANSLATION_CONSTANTS,
          FOLLOWER_ROTATION_CONSTANTS,
          FOLLOWER_FEEDFORWARD_CONSTANTS
  );

  private PidController snapRotationController = new PidController(SNAP_ROTATION_CONSTANTS);
  private double snapRotation = Double.NaN;

  public double lastTimestamp = 0;

  private final Object lock = new Object();
  private HolonomicDriveSignal signal = new HolonomicDriveSignal(Vector2.ZERO, 0.0, false);
  private Trajectory.Segment segment = null;

  public DrivetrainSubsystem() {


      double frontLeftAngleOffset = FRONT_LEFT_ANGLE_OFFSET_COMPETITION;
      double frontRightAngleOffset = FRONT_RIGHT_ANGLE_OFFSET_COMPETITION;
      double backLeftAngleOffset = BACK_LEFT_ANGLE_OFFSET_COMPETITION;
      double backRightAngleOffset = BACK_RIGHT_ANGLE_OFFSET_COMPETITION;
    //   if (Superstructure.getInstance().isPracticeBot()) {
    //       frontLeftAngleOffset = FRONT_LEFT_ANGLE_OFFSET_PRACTICE;
    //       frontRightAngleOffset = FRONT_RIGHT_ANGLE_OFFSET_PRACTICE;
    //       backLeftAngleOffset = BACK_LEFT_ANGLE_OFFSET_PRACTICE;
    //       backRightAngleOffset = BACK_RIGHT_ANGLE_OFFSET_PRACTICE;
    //   }

      SwerveModule frontLeftModule = new Mk2SwerveModule(
              new Vector2(-TRACKWIDTH / 2.0, WHEELBASE / 2.0),
              frontLeftAngleOffset,
              new CANSparkMax(RobotMap.DRIVETRAIN_FRONT_LEFT_ANGLE_MOTOR, CANSparkMaxLowLevel.MotorType.kBrushless),
              new CANSparkMax(RobotMap.DRIVETRAIN_FRONT_LEFT_DRIVE_MOTOR, CANSparkMaxLowLevel.MotorType.kBrushless),
              new AnalogInput(RobotMap.DRIVETRAIN_FRONT_LEFT_ANGLE_ENCODER)
      );
      frontLeftModule.setName("Front Left");

      SwerveModule frontRightModule = new Mk2SwerveModule(
              new Vector2(TRACKWIDTH / 2.0, WHEELBASE / 2.0),
              frontRightAngleOffset,
              new CANSparkMax(RobotMap.DRIVETRAIN_FRONT_RIGHT_ANGLE_MOTOR, CANSparkMaxLowLevel.MotorType.kBrushless),
              new CANSparkMax(RobotMap.DRIVETRAIN_FRONT_RIGHT_DRIVE_MOTOR, CANSparkMaxLowLevel.MotorType.kBrushless),
              new AnalogInput(RobotMap.DRIVETRAIN_FRONT_RIGHT_ANGLE_ENCODER)
      );
      frontRightModule.setName("Front Right");

      SwerveModule backLeftModule = new Mk2SwerveModule(
              new Vector2(-TRACKWIDTH / 2.0, -WHEELBASE / 2.0),
              backLeftAngleOffset,
              new CANSparkMax(RobotMap.DRIVETRAIN_BACK_LEFT_ANGLE_MOTOR, CANSparkMaxLowLevel.MotorType.kBrushless),
              new CANSparkMax(RobotMap.DRIVETRAIN_BACK_LEFT_DRIVE_MOTOR, CANSparkMaxLowLevel.MotorType.kBrushless),
              new AnalogInput(RobotMap.DRIVETRAIN_BACK_LEFT_ANGLE_ENCODER)
      );
      backLeftModule.setName("Back Left");

      SwerveModule backRightModule = new Mk2SwerveModule(
              new Vector2(TRACKWIDTH / 2.0, -WHEELBASE / 2.0),
              backRightAngleOffset,
              new CANSparkMax(RobotMap.DRIVETRAIN_BACK_RIGHT_ANGLE_MOTOR, CANSparkMaxLowLevel.MotorType.kBrushless),
              new CANSparkMax(RobotMap.DRIVETRAIN_BACK_RIGHT_DRIVE_MOTOR, CANSparkMaxLowLevel.MotorType.kBrushless),
              new AnalogInput(RobotMap.DRIVETRAIN_BACK_RIGHT_ANGLE_ENCODER)
      );
      backRightModule.setName("Back Right");

      swerveModules = new SwerveModule[]{
              frontLeftModule,
              frontRightModule,
              backLeftModule,
              backRightModule,
      };

      snapRotationController.setInputRange(0.0, 2.0 * Math.PI);
      snapRotationController.setContinuous(true);
  }

  public void setSnapRotation(double snapRotation) {
      synchronized (lock) {
          this.snapRotation = snapRotation;
      }
  }

  @Override
  public void holonomicDrive(Vector2 translation, double rotation, boolean fieldOriented) {
      synchronized (lock) {
          this.signal = new HolonomicDriveSignal(translation, rotation, fieldOriented);
      }
  }

  public boolean autonomousDriveFinished(Vector2 translation) {
    Vector2 kinematicPosition = getKinematicPosition();

    Vector2 Delta = kinematicPosition.subtract(translation);
    
    //System.out.println("Delta.length: " + Delta.length);
    //System.out.println("kinematicPosition: " + kinematicPosition);
    

    if(Delta.length < 50) 
        return true;
    else
        return false;    
  }

  

public static ArrayList<HolonomicDriveSignal> readDriveRecording(String fileName, boolean isFieldOriented) {
    ArrayList<HolonomicDriveSignal> driveRecording = new ArrayList<HolonomicDriveSignal>();
    
    try
    {
        File file = new File(fileName); 
        BufferedReader br = new BufferedReader(new FileReader(file)); 
        
        String line; 
        int lineIndex = 0;
        while ((line = br.readLine()) != null) {
            lineIndex++;
            String[] recordStr = line.split(",", 0);
            if (recordStr.length == 3) {
                Double x = Double.valueOf(recordStr[0]);
                Double y = Double.valueOf(recordStr[1]);
                Double rotation = Double.valueOf(recordStr[2]);

                Vector2 translation = new Vector2(x,y);

                driveRecording.add(new HolonomicDriveSignal(translation, rotation, isFieldOriented));

            } else {
                throw new Exception("Error reading drive file on line " + lineIndex );
            }

        } 
        br.close();
    }
    catch(Exception err) {
        System.out.println("Error reading drive file:" + fileName);
        System.out.println(err.getMessage());
    }
    
    return driveRecording;
}


//  public void driveAlongPath(Path2D path, Double extraTime, Boolean resetOdometry) {
//     println("Driving along path ${path.name}, duration: ${path.durationWithSpeed}, travel direction: ${path.robotDirection}, mirrored: ${path.isMirrored}")

//     if (resetOdometry) {
//         System.out.println("Position = $position Heading = $heading");
//         resetOdometry();

//         // set to the numbers required for the start of the path
//         position = path.getPosition(0.0);
//         heading = path.getTangent(0.0).angle.degrees + path.headingCurve.getValue(0.0).degrees;
//         System.out.println("After Reset Position = " + position + "Heading = " + heading);
//     }
//     double prevTime = 0.0;

//     TImer timer = new Timer();
//     timer.start();
//     prevPathPosition = path.getPosition(0.0);
//     periodic {
//         double t = timer.get();
//         double dt = t - prevTime;


//         // position error
//         Vector2 pathPosition = path.getPosition(t);
//         val positionError = pathPosition - position;
//         //println("pathPosition=$pathPosition position=$position positionError=$positionError")

//         // position feed forward
//         double pathVelocity = (pathPosition - prevPathPosition)/dt;
//         prevPathPosition = pathPosition;

//         frc.lib.math.Vector2 translationControlField = pathVelocity * parameters.kPositionFeedForward + positionError * parameters.kPosition;

//         // heading error
//         double robotHeading = heading;
//         double pathHeadingRadiansDouble = path.getTangent(t).angle + path.headingCurve.getValue(t);
//         double pathHeading = pathHeadingRadiansDouble.radians;
//         double headingError = (pathHeading - robotHeading).wrap();
//         //println("GyroHeading=$robotHeading PathHeading=$pathHeading AngleError=$angleError")

//         // heading feed forward
//         double headingVelocity = (pathHeading.asDegrees - prevPathHeading.asDegrees)/dt;
//         prevPathHeading = pathHeading;

//         double turnControl = headingVelocity * parameters.kHeadingFeedForward + headingError.asDegrees * parameters.kHeading;

//         // send it
//         holonomicDrive(translationControlField, turnControl, true);

//         // are we done yet?
//         if (t >= path.durationWithSpeed + extraTime) {
//            stop();
//         }


//         prevTime = t;

//         //        println("Time=$t Path Position=$pathPosition Position=$position")
//         //        println("DT$dt Path Velocity = $pathVelocity Velocity = $velocity")
//     }

//     // shut it down
//     holonomicDrive(Vector2.ZERO, 0.0, true);
// }
  
  @Override
  public synchronized void updateKinematics(double timestamp) {
    
      super.updateKinematics(timestamp);
        //System.out.println("Sybsystem.updateKinematics");
      double dt = timestamp - lastTimestamp;
      lastTimestamp = timestamp;
      

      double localSnapRotation;
      synchronized (lock) {
          localSnapRotation = snapRotation;
      }

      double gyroRate = getGyroscope().getRate();
      Vector2 kinematicVelocity = getKinematicVelocity();
      Rotation2 gyroAngle = getGyroscope().getAngle();
      //SmartDashboard.putNumber("gryoAngle", gyroAngle.toDegrees());
      //Rotation2 gyroAngle = new Rotation2(1, 0, false);
      Vector2 kinematicPosition = getKinematicPosition();
      RigidTransform2 rigidTransform = new RigidTransform2(kinematicPosition,  gyroAngle);
      
    //   SmartDashboard.putNumber("Gyro Rate: ", gyroRate);
    //   SmartDashboard.putNumber("Kinematic Velocity: ", kinematicVelocity.x);
    //   SmartDashboard.putNumber("Gyro Angle: ", gyroAngle.toDegrees());
    //   SmartDashboard.putNumber("Kinematic Position: ", kinematicPosition.length);
    //   //SmartDashboard.putNumber("Rigid Transform: ", rigidTransform.);

      Optional<HolonomicDriveSignal> optSignal = follower.update(rigidTransform, kinematicVelocity, gyroRate, timestamp, dt);
      HolonomicDriveSignal localSignal;

      if (optSignal.isPresent()) {
          localSignal = optSignal.get();

          synchronized (lock) {
              signal = localSignal;
              segment = follower.getLastSegment();
          }
      } else {
          synchronized (lock) {
              localSignal = this.signal;
          }
      }

    //   SmartDashboard.putNumber("Signal Forward", localSignal.getTranslation().x);
    //   SmartDashboard.putNumber("Signal Strafe", localSignal.getTranslation().y);
    //   SmartDashboard.putNumber("Signal Rotation",localSignal.getRotation());

    //   if(follower.getCurrentPose() != null) {
    //     SmartDashboard.putNumber("Pose X", follower.getCurrentPose().translation.x);
    //     SmartDashboard.putNumber("Pose Y", follower.getCurrentPose().translation.y);
    //     SmartDashboard.putNumber("Pose Rotation", follower.getCurrentPose().rotation.toDegrees());
    //   }

      if(follower.getLastSegment() != null) {
        // SmartDashboard.putNumber("lastSegment translation x", follower.getLastSegment().translation.x);
        // SmartDashboard.putNumber("lastSegment translation y", follower.getLastSegment().translation.y);
        // SmartDashboard.putNumber("lastSegment rotation", follower.getLastSegment().rotation.toDegrees());
      }
      


      if (Math.abs(localSignal.getRotation()) < 0.1 && Double.isFinite(localSnapRotation)) {
          snapRotationController.setSetpoint(localSnapRotation);

          localSignal = new HolonomicDriveSignal(localSignal.getTranslation(),
                  snapRotationController.calculate(getGyroscope().getAngle().toRadians(), dt),

                  localSignal.isFieldOriented());
      } else {
          synchronized (lock) {
              snapRotation = Double.NaN;
          }
      }

      //System.out.println("translation: " + localSignal.getTranslation() + " rotation: " + localSignal.getRotation());
      super.holonomicDrive(localSignal.getTranslation(), localSignal.getRotation(), localSignal.isFieldOriented());
  }

 
  @Override
  public void outputToSmartDashboard() {
     // super.outputToSmartDashboard();

      // HolonomicDriveSignal localSignal;
      // Trajectory.Segment localSegment;
      // synchronized (lock) {
      //     localSignal = signal;
      //     localSegment = segment;
      // }

    //   SmartDashboard.putNumber("Drivetrain Follower Forwards", localSignal.getTranslation().x);
    //   SmartDashboard.putNumber("Drivetrain Follower Strafe", localSignal.getTranslation().y);
    //   SmartDashboard.putNumber("Drivetrain Follower Rotation", localSignal.getRotation());
    //   SmartDashboard.putBoolean("Drivetrain Follower Field Oriented", localSignal.isFieldOriented());

      // if (follower.getCurrentTrajectory().isPresent() && localSegment != null) {
      //     //SmartDashboard.putNumber("Drivetrain Follower Target Angle", localSegment.rotation.toDegrees());

      //    // Vector2 position = getKinematicPosition();

      //   //   SmartDashboard.putNumber("Drivetrain Follower X Error", localSegment.translation.x - position.x);
      //   //   SmartDashboard.putNumber("Drivetrain Follower Y Error", localSegment.translation.y - position.y);
      //   //   SmartDashboard.putNumber("Drivetrain Follower Angle Error", localSegment.rotation.toDegrees() - getGyroscope().getAngle().toDegrees());
      // }
  }

//   public static DrivetrainSubsystem getInstance() {
//       return instance;
//   }

  @Override
  public SwerveModule[] getSwerveModules() {
      return swerveModules;
  }

  @Override
  public Gyroscope getGyroscope() {
      return navX;
  }

  @Override
  public double getMaximumVelocity() {
      return 0;
  }

  @Override
  public double getMaximumAcceleration() {
      return 0;
  }

  @Override
  protected void initDefaultCommand() {
      setDefaultCommand(new HolonomicDriveCommand(DrivetrainSubsystem.ControlMode.DualStick));
  }

  public TrajectoryFollower<HolonomicDriveSignal> getFollower() {
      return follower;
    }
    public enum ControlMode{
    DualStick, 
    SingleStick,
    Controller
    };
}